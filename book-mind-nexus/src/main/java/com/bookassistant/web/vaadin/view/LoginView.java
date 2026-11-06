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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.H1;

@Route(value = "login", layout = MainLayout.class)
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(true);
        setSpacing(true);

        H1 title = new H1("Login");
        EmailField email = new EmailField("Email");
        email.setWidth("300px");
        email.setRequired(true);

        PasswordField password = new PasswordField("Password");
        password.setWidth("300px");
        password.setRequired(true);

        Button loginButton = new Button("Login", e -> {
            // TODO: Implement login logic
        });
        loginButton.setWidth("300px");
        loginButton.getStyle().set("margin-top", "1rem");

        add(new H1("Login"), email, password, loginButton);
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        setSpacing(true);
    }
}