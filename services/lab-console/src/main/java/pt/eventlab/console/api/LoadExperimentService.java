package pt.eventlab.console.api;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pt.eventlab.console.domain.ExperimentRunRegistry;
import pt.eventlab.console.domain.LoadExperiment;
import pt.eventlab.console.domain.LoadExperimentRepository;
import pt.eventlab.contracts.ExperimentPlan;
import pt.eventlab.contracts.FulfilmentBehavior;

@Service
class LoadExperimentService {

    private static final List<String> TERMINAL_STATES =
            List.of("COMPLETED", "COMPENSATED", "FAILED_REQUIRES_INTERVENTION");
    private final Clock clock = Clock.systemUTC();
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final ExecutorService launchers = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore launchAdmission = new Semaphore(8);
    private final ReentrantLock progressLock = new ReentrantLock();
    private final LoadExperimentRepository experiments;
    private final ExperimentRunRegistry runs;
    private final WorkflowClient workflows;
    private final EvidenceReportService evidence;
    private final DeploymentControlService deployment;
    private final DeploymentProperties deploymentProperties;
    private final ConsumerConcurrencyClient concurrency;

    LoadExperimentService(LoadExperimentRepository experiments, ExperimentRunRegistry runs,
            WorkflowClient workflows, EvidenceReportService evidence,
            DeploymentControlService deployment, DeploymentProperties deploymentProperties,
            ConsumerConcurrencyClient concurrency) {
        this.experiments = experiments;
        this.runs = runs;
        this.workflows = workflows;
        this.evidence = evidence;
        this.deployment = deployment;
        this.deploymentProperties = deploymentProperties;
        this.concurrency = concurrency;
    }

    public synchronized LoadExperimentResponse start(StartLoadExperimentRequest request) {
        deployment.requireAcceptingExperiments();
        validate(request);
        if (experiments.existsByStatusIn(List.of("LAUNCHING", "RUNNING"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only one load experiment can run at a time");
        }
        String pattern = request.trafficPattern().toUpperCase();
        int interval = "BURST".equals(pattern) ? 0 : request.intervalMillis();
        concurrency.configure(request.consumerConcurrency());
        LoadExperiment experiment = experiments.save(new LoadExperiment(UUID.randomUUID(), pattern,
                request.workflowCount(), request.duplicatePercentage(), interval,
                request.consumerConcurrency(), clock.instant()));
        coordinator.submit(() -> launch(experiment));
        return response(experiment);
    }

    public List<LoadExperimentResponse> recent() {
        return experiments.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 12)).stream()
                .map(this::response).toList();
    }

    public synchronized LoadExperimentResponse inspect(UUID id) {
        return response(find(id));
    }

    @Scheduled(fixedDelay = 1000)
    public void assessRunningExperiments() {
        for (LoadExperiment experiment : experiments.findByStatus("RUNNING")) {
            LoadExperimentResponse report = response(experiment);
            if (report.acceptedWorkflows() > 0
                    && report.terminalWorkflows() == report.acceptedWorkflows()) {
                experiment.completed(report.invariantViolations() == 0, clock.instant());
                experiments.save(experiment);
                concurrency.configure(1);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markLaunchesAbandonedByRestart() {
        boolean abandoned = false;
        for (LoadExperiment experiment : experiments.findByStatus("LAUNCHING")) {
            experiment.completed(false, clock.instant());
            experiments.save(experiment);
            abandoned = true;
        }
        if (abandoned) concurrency.configure(1);
    }

    private void launch(LoadExperiment experiment) {
        var completions = new ExecutorCompletionService<Void>(launchers);
        for (int index = 0; index < experiment.requestedWorkflows(); index++) {
            int member = index;
            completions.submit(() -> {
                launchAdmission.acquire();
                try {
                    if (experiment.intervalMillis() > 0) {
                        Thread.sleep((long) member * experiment.intervalMillis());
                    }
                    boolean duplicate = member * 100 < experiment.requestedWorkflows()
                            * experiment.duplicatePercentage();
                    ExperimentPlan plan = new ExperimentPlan(duplicate ? 2 : 1, FulfilmentBehavior.SUCCESS);
                    StartRunRequest request = new StartRunRequest("load-" + experiment.id(), plan,
                            BigDecimal.valueOf(129.90), "EUR");
                    RunResponse run = workflows.start(request);
                    runs.register(request, run);
                    recordAccepted(experiment.id(), run.workflowId());
                } catch (Exception exception) {
                    recordLaunchFailure(experiment.id());
                } finally {
                    launchAdmission.release();
                }
                return null;
            });
        }
        for (int completed = 0; completed < experiment.requestedWorkflows(); completed++) {
            try {
                completions.take().get();
            } catch (Exception exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LoadExperiment current = find(experiment.id());
        current.launchCompleted(clock.instant());
        experiments.save(current);
    }

    private void recordAccepted(UUID experimentId, UUID workflowId) {
        progressLock.lock();
        try {
            LoadExperiment current = find(experimentId);
            current.accepted(workflowId);
            experiments.save(current);
        } finally {
            progressLock.unlock();
        }
    }

    private void recordLaunchFailure(UUID experimentId) {
        progressLock.lock();
        try {
            LoadExperiment current = find(experimentId);
            current.launchFailed();
            experiments.save(current);
        } finally {
            progressLock.unlock();
        }
    }

    private LoadExperimentResponse response(LoadExperiment experiment) {
        List<Member> members = experiment.workflowIds().stream().map(this::member).toList();
        List<Member> terminal = members.stream().filter(Member::terminal).toList();
        List<Long> latencies = terminal.stream().map(Member::latencyMillis).sorted().toList();
        int proved = (int) terminal.stream().filter(Member::proved).count();
        int violations = (int) terminal.stream().filter(member -> !member.proved()).count();
        int paymentObserved = (int) members.stream().filter(Member::paymentObserved).count();
        int fulfilmentObserved = (int) members.stream().filter(Member::fulfilmentObserved).count();
        int duplicates = members.stream().mapToInt(Member::duplicateDeliveries).sum();
        int maxInFlight = maxInFlight(members);
        double throughput = throughput(terminal);
        long launchDuration = elapsed(experiment.createdAt(), experiment.launchedAt());
        long firstPaymentDelay = delayFrom(experiment.createdAt(), members.stream()
                .map(Member::paymentAt).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null));
        long firstFulfilmentDelay = delayFrom(experiment.createdAt(), members.stream()
                .map(Member::fulfilmentAt).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null));
        long firstTerminalDelay = delayFrom(experiment.createdAt(), terminal.stream()
                .map(Member::endedAt).min(Instant::compareTo).orElse(null));
        long drainDuration = delayFrom(experiment.createdAt(), terminal.stream()
                .map(Member::endedAt).max(Instant::compareTo).orElse(null));
        int processedLaunches = members.size() + experiment.launchFailures();
        String statusReason = "FAILED".equals(experiment.status())
                && processedLaunches < experiment.requestedWorkflows()
                ? "LAUNCH_INTERRUPTED"
                : "FAILED".equals(experiment.status()) ? "EVIDENCE_FAILED" : null;
        return new LoadExperimentResponse(experiment.id(), experiment.status(), statusReason,
                experiment.trafficPattern(), experiment.consumerConcurrency(),
                experiment.requestedWorkflows(), processedLaunches,
                experiment.requestedWorkflows() - processedLaunches,
                members.size(), experiment.launchFailures(),
                experiment.duplicatePercentage(), paymentObserved, fulfilmentObserved,
                terminal.size(), proved, violations, duplicates,
                members.size() - terminal.size(), maxInFlight, throughput,
                percentile(latencies, 0.5), percentile(latencies, 0.95),
                launchDuration, firstPaymentDelay, firstFulfilmentDelay, firstTerminalDelay, drainDuration,
                experiment.createdAt(),
                experiment.completedAt(), experiment.workflowIds());
    }

    private Member member(UUID workflowId) {
        RunDetailsResponse run = runs.details(workflowId);
        boolean terminal = TERMINAL_STATES.contains(run.state());
        Instant ended = terminal && !run.timeline().isEmpty()
                ? run.timeline().get(run.timeline().size() - 1).occurredAt() : null;
        boolean proved = terminal && "PROVED".equals(evidence.report(run).assessment());
        boolean paymentObserved = run.timeline().stream()
                .anyMatch(event -> "payment.authorized".equals(event.eventType()));
        boolean fulfilmentObserved = run.timeline().stream()
                .anyMatch(event -> "fulfilment.completed".equals(event.eventType()));
        int duplicates = (int) run.timeline().stream().filter(TimelineEventResponse::duplicateDelivery).count();
        Instant paymentAt = firstEventAt(run.timeline(), "payment.authorized");
        Instant fulfilmentAt = firstEventAt(run.timeline(), "fulfilment.completed");
        return new Member(run.createdAt(), ended, terminal, proved,
                paymentObserved, fulfilmentObserved, duplicates, paymentAt, fulfilmentAt);
    }

    private Instant firstEventAt(List<TimelineEventResponse> timeline, String eventType) {
        return timeline.stream().filter(event -> eventType.equals(event.eventType()))
                .map(TimelineEventResponse::occurredAt).min(Instant::compareTo).orElse(null);
    }

    private long elapsed(Instant started, Instant ended) {
        return ended == null ? 0 : Math.max(0, Duration.between(started, ended).toMillis());
    }

    private long delayFrom(Instant started, Instant observed) {
        return observed == null ? 0 : elapsed(started, observed);
    }

    private int maxInFlight(List<Member> members) {
        record Point(Instant at, int delta) { }
        List<Point> points = new ArrayList<>();
        members.forEach(member -> {
            points.add(new Point(member.startedAt(), 1));
            if (member.endedAt() != null) points.add(new Point(member.endedAt(), -1));
        });
        points.sort(Comparator.comparing(Point::at).thenComparing(Point::delta));
        int current = 0;
        int maximum = 0;
        for (Point point : points) {
            current += point.delta();
            maximum = Math.max(maximum, current);
        }
        return maximum;
    }

    private double throughput(List<Member> terminal) {
        if (terminal.isEmpty()) return 0;
        Instant first = terminal.stream().map(Member::startedAt).min(Instant::compareTo).orElseThrow();
        Instant last = terminal.stream().map(Member::endedAt).max(Instant::compareTo).orElseThrow();
        double seconds = Math.max(0.001, Duration.between(first, last).toMillis() / 1000.0);
        return Math.round(terminal.size() / seconds * 100.0) / 100.0;
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(values.size() * percentile) - 1);
        return values.get(index);
    }

    private void validate(StartLoadExperimentRequest request) {
        int maximum = "local".equalsIgnoreCase(deploymentProperties.environment()) ? 100 : 25;
        if (request.workflowCount() < 1 || request.workflowCount() > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "workflowCount must be between 1 and " + maximum + " in this environment");
        }
        if (request.trafficPattern() == null
                || !List.of("BURST", "STEADY").contains(request.trafficPattern().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "trafficPattern must be BURST or STEADY");
        }
        if (request.duplicatePercentage() < 0 || request.duplicatePercentage() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "duplicatePercentage must be between 0 and 100");
        }
        if (!List.of(1, 4, 8).contains(request.consumerConcurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "consumerConcurrency must be 1, 4, or 8");
        }
        if ("STEADY".equalsIgnoreCase(request.trafficPattern())
                && (request.intervalMillis() < 50 || request.intervalMillis() > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "intervalMillis must be between 50 and 2000 for steady traffic");
        }
    }

    private LoadExperiment find(UUID id) {
        return experiments.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Load experiment not found"));
    }

    @PreDestroy
    void close() {
        coordinator.shutdownNow();
        launchers.shutdownNow();
    }

    private record Member(Instant startedAt, Instant endedAt, boolean terminal,
                          boolean proved, boolean paymentObserved,
                          boolean fulfilmentObserved, int duplicateDeliveries,
                          Instant paymentAt, Instant fulfilmentAt) {
        long latencyMillis() { return Duration.between(startedAt, endedAt).toMillis(); }
    }
}
