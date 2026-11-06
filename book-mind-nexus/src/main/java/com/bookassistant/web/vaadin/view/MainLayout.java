package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@AnonymousAllowed
public class MainLayout extends com.vaadin.flow.component.applayout.AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        setDrawerOpened(false);

        // Header
        H1 logo = new H1("🧠 BookMind Nexus");
        logo.getStyle().set("font-size", "1.5rem").set("margin", "0");
        
        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), logo);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.getStyle().set("padding", "0 1rem");
        addToNavbar(header);

        // Drawer content
        VerticalLayout drawerContent = new VerticalLayout();
        drawerContent.setPadding(false);
        drawerContent.setSpacing(false);
        drawerContent.setSizeFull();

// Navigation links
        addDrawerItem("Home", HomeView.class);
        addDrawerItem("About", AboutView.class);
        addDrawerItem("Contact", ContactView.class);
        addDrawerItem("Login", LoginView.class);
        addDrawerItem("Register", RegisterView.class);
    }

    private void addDrawerItem(String text, Class<? extends com.vaadin.flow.component.Component> targetView) {
        RouterLink link = new RouterLink(text, targetView);
        link.getStyle().set("padding", "0.5rem 1rem").set("text-decoration", "none").set("color", "var(--lumo-body-text-color)");
        link.getElement().getStyle().set("display", "block");
        addToDrawer(link);
    }
}