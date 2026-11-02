package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class RegisterPageRenderer {
    private RegisterPageRenderer() {}

    public static String render(PageContext ctx) {
        return renderWithMessage(ctx, null);
    }

    public static String renderWithError(PageContext ctx, String error) {
        return renderWithMessage(ctx, error);
    }

    private static String renderWithMessage(PageContext ctx, String error) {
        return HtmlBuilder.create()
            .doctype()
            .html(b -> b
                .head(h -> h
                    .meta("charset", "UTF-8")
                    .meta("name", "viewport", "content", "width=device-width, initial-scale=1.0")
                    .title(ctx.getMessage("register.title"))
                    .link("https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.rtl.min.css", "stylesheet")
                    .link("https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700&display=swap", "stylesheet")
                    .link("/styles.css", "stylesheet")
                )
                .body(body -> body
                    .nav(nav -> LayoutRenderer.renderNavbar(nav, ctx))
                    .div(main -> main
                        .div(container -> container
                            .div(card -> card
                                .div(header -> header
                                    .h2(ctx.getMessage("register.title"))
                                ).attr("class", "card-header")
                                .div(bd -> bd
                                    .raw(error != null ? "<div class=\"alert alert-danger\">" + escapeHtml(error) + "</div>" : "")
                                    .form("/register", "post", form -> form
                                        .div(group -> group
                                            .label(ctx.getMessage("register.name")).attr("for", "name")
                                            .input("text", "name", "id", "name", "class", "form-control")
                                        ).attr("class", "mb-3")
                                        .div(group -> group
                                            .label(ctx.getMessage("register.email")).attr("for", "email")
                                            .input("email", "email", "id", "email", "class", "form-control", "required", "required")
                                        ).attr("class", "mb-3")
                                        .div(group -> group
                                            .label(ctx.getMessage("register.password")).attr("for", "password")
                                            .input("password", "password", "id", "password", "class", "form-control", "required", "required")
                                        ).attr("class", "mb-3")
                                        .div(group -> group
                                            .label(ctx.getMessage("register.confirm")).attr("for", "confirm")
                                            .input("password", "confirm", "id", "confirm", "class", "form-control", "required", "required")
                                        ).attr("class", "mb-3")
                                        .button(btn -> btn.text(ctx.getMessage("register.submit"))).attr("type", "submit").attr("class", "btn btn-primary w-100")
                                    )
                                    .div(links -> links
                                        .a("/login", a -> a.text(ctx.getMessage("login.title"))).attr("class", "text-muted")
                                    ).attr("class", "text-center mt-3")
                                ).attr("class", "card-body")
                            ).attr("class", "card auth-card")
                        ).attr("class", "container py-5 d-flex justify-content-center")
                    )
                    .footer(f -> LayoutRenderer.renderFooter(f, ctx))
                    .script(s -> LayoutRenderer.renderScripts(s, ctx))
                )
            )
            .build();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}