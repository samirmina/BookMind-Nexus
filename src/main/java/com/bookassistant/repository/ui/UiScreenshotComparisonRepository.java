package com.bookassistant.repository.ui;

import com.bookassistant.entity.ui.UiScreenshotComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UiScreenshotComparisonRepository extends JpaRepository<UiScreenshotComparison, Long> {
    List<UiScreenshotComparison> findByBaselineIdOrderByCreatedAtDesc(Long baselineId);
}
