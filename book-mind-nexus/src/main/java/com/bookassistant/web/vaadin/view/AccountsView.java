package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "accounts", layout = MainLayout.class)
@PageTitle("Accounts")
public class AccountsView extends VerticalLayout {

    public AccountsView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H1("Accounts"));
        add(new com.vaadin.flow.component.html.H2("Linked Accounts"));
        // TODO: Add grid for linked accounts
        // TODO: Add Notion integration section
        // TODO: Add sync status and sync button
    }
}