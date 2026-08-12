package pt.eventlab.console.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExperimentRunRepository extends JpaRepository<ExperimentRun, UUID> {
    List<ExperimentRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
