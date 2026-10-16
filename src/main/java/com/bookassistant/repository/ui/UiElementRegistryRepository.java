package com.bookassistant.repository.ui;

import com.bookassistant.entity.ui.UiElementRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UiElementRegistryRepository extends JpaRepository<UiElementRegistry, Long> {
    List<UiElementRegistry> findByPagePathAndUserStateOrderByPriorityDesc(String pagePath, String userState);
    List<UiElementRegistry> findByPagePath(String pagePath);
}
