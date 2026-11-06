package com.bookassistant.web.vaadin.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "register", layout = MainLayout.class)
@PageTitle("Register")
@AnonymousAllowed
public class RegisterView extends VerticalLayout {

    public RegisterView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(true);
        setSpacing(true);

        H1 title = new H1("Create Account");
        TextField name = new TextField("Name");
        name.setWidth("300px");
        EmailField email = new EmailField("Email");
        email.setWidth("300px");
        email.setRequired(true);
        PasswordField password = new PasswordField("Password");
        password.setWidth("300px");
        password.setRequired(true);
        PasswordField confirm = new PasswordField("Confirm Password");
        confirm.setWidth("300px");
        confirm.setRequired(true);

        Button registerButton = new Button("Register", e -> {
            // TODO: Implement register logic
        });
        registerButton.setWidth("300px");
        registerButton.getStyle().set("margin-top", "1rem");

        add(new H1("Register"), new TextField("Name"), email, password, confirm, registerButton);
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        setSpacing(true);
    }
}