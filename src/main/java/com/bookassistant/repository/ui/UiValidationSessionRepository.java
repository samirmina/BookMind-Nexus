package com.bookassistant.repository.ui;

import com.bookassistant.entity.ui.UiValidationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UiValidationSessionRepository extends JpaRepository<UiValidationSession, Long> {
    List<UiValidationSession> findBySessionIdOrderByStartedAtDesc(String sessionId);
}
