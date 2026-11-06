package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "contact", layout = MainLayout.class)
@PageTitle("Contact")
public class ContactView extends VerticalLayout {

    public ContactView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        add(new H1("Contact Us"));
        add(new com.vaadin.flow.component.html.Paragraph("We'd love to hear from you."));
        
        TextField name = new TextField("Name");
        name.setWidth("400px");
        name.setRequired(true);
        
        com.vaadin.flow.component.textfield.EmailField email = new com.vaadin.flow.component.textfield.EmailField("Email");
        email.setWidth("400px");
        email.setRequired(true);
        
        TextField subject = new TextField("Subject");
        subject.setWidth("400px");
        subject.setRequired(true);
        
        com.vaadin.flow.component.textfield.TextArea message = new com.vaadin.flow.component.textfield.TextArea("Message");
        message.setWidth("400px");
        message.setHeight("150px");
        message.setRequired(true);
        
        com.vaadin.flow.component.button.Button submit = new com.vaadin.flow.component.button.Button("Send", e -> {
            // TODO: Implement contact form submission
        });
        submit.getStyle().set("margin-top", "1rem");
        
        add(new com.vaadin.flow.component.html.H1("Contact Us"), 
            new com.vaadin.flow.component.textfield.TextField("Name"), 
            new com.vaadin.flow.component.textfield.EmailField("Email"),
            new com.vaadin.flow.component.textfield.TextField("Subject"),
            new com.vaadin.flow.component.textfield.TextArea("Message"),
            new com.vaadin.flow.component.button.Button("Send"));
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        setSpacing(true);
    }
}