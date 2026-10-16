package com.bookassistant.repository.ui;

import com.bookassistant.entity.ui.UiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UiSnapshotRepository extends JpaRepository<UiSnapshot, Long> {
    List<UiSnapshot> findByPagePathOrderByCreatedAtDesc(String pagePath);
    List<UiSnapshot> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<UiSnapshot> findByUserIdOrderByCreatedAtDesc(Long userId);
}
