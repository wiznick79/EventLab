package pt.eventlab.console.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadExperimentRepository extends JpaRepository<LoadExperiment, UUID> {
    List<LoadExperiment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByStatusIn(List<String> statuses);
    List<LoadExperiment> findByStatus(String status);
}
