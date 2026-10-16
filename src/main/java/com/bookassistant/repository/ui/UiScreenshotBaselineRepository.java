package com.bookassistant.repository.ui;

import com.bookassistant.entity.ui.UiScreenshotBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UiScreenshotBaselineRepository extends JpaRepository<UiScreenshotBaseline, Long> {
    Optional<UiScreenshotBaseline> findByPagePathAndUserStateAndIsActiveTrue(String pagePath, String userState);
    List<UiScreenshotBaseline> findByPagePathAndIsActiveTrue(String pagePath);
}
