package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "about", layout = MainLayout.class)
@PageTitle("About")
@AnonymousAllowed
public class AboutView extends VerticalLayout {

    public AboutView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        add(new H1("About BookMind Nexus"));
        add(new Paragraph("BookMind Nexus is a knowledge integration platform that helps you extract insights from your books using AI."));
        
        add(new H2("Philosophy"));
        add(new Paragraph("We believe that knowledge should be accessible and interconnected. Our platform helps you extract, organize, and connect insights from your books."));
    }
}