package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class LoginPageRenderer {
    private LoginPageRenderer() {}

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
                    .title(ctx.getMessage("login.title"))
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
                                    .h2(ctx.getMessage("login.title"))
                                ).attr("class", "card-header")
                                .div(bd -> bd
                                    .raw(error != null ? "<div class=\"alert alert-danger\">" + escapeHtml(error) + "</div>" : "")
                                    .form("/login", "post", form -> form
                                        .input("hidden", ctx.csrfParameterName(), "value", ctx.csrfToken())
                                        .div(group -> group
                                            .label(ctx.getMessage("login.email")).attr("for", "email")
                                            .input("email", "email", "id", "email", "class", "form-control", "required", "required", "autofocus", "autofocus")
                                        ).attr("class", "mb-3")
                                        .div(group -> group
                                            .label(ctx.getMessage("login.password")).attr("for", "password")
                                            .input("password", "password", "id", "password", "class", "form-control", "required", "required")
                                        ).attr("class", "mb-3")
                                        .div(group -> group
                                            .input("checkbox", "remember", "id", "remember", "class", "form-check-input")
                                            .label(ctx.getMessage("login.remember")).attr("for", "remember").attr("class", "form-check-label")
                                        ).attr("class", "mb-3 form-check")
                                        .button(btn -> btn.text(ctx.getMessage("login.submit"))).attr("type", "submit").attr("class", "btn btn-primary w-100")
                                    )
                                    .div(links -> links
                                        .a("/register", a -> a.text(ctx.getMessage("register.title"))).attr("class", "text-muted")
                                        .raw(" | ")
                                        .a("/forgot-password", a -> a.text(ctx.getMessage("login.forgot"))).attr("class", "text-muted")
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