package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "app", layout = MainLayout.class)
@PageTitle("App - BookMind Nexus")
public class AppView extends VerticalLayout {

    public AppView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H1("BookMind Nexus App"));
        // TODO: Add main app interface with:
        // - Book selection
        // - Page range selector
        // - AI service buttons (QA, Summary, Analysis, Search, Comprehension, Plan)
        // - Results display area
        // - Outputs history
    }
}