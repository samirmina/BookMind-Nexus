package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "outputs", layout = MainLayout.class)
@PageTitle("Outputs")
public class OutputsView extends VerticalLayout {

    public OutputsView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H1("Outputs"));
        add(new com.vaadin.flow.component.html.H2("Your Saved Outputs"));
        
        // TODO: Add Grid with outputs data
        // Grid<Output> grid = new Grid<>(Output.class);
        // grid.setColumns("id", "tabUsed", "createdAt");
        // grid.addColumn(o -> "Delete").setRenderer(...);
    }
}