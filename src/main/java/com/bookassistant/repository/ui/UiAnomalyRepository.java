package com.bookassistant.repository.ui;

import com.bookassistant.entity.ui.UiAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UiAnomalyRepository extends JpaRepository<UiAnomaly, Long> {
    List<UiAnomaly> findByPagePathOrderByCreatedAtDesc(String pagePath);
    List<UiAnomaly> findBySeverityOrderByCreatedAtDesc(String severity);
    List<UiAnomaly> findBySnapshotId(Long snapshotId);
}
