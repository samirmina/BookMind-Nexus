package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class ContactPageRenderer {
    private ContactPageRenderer() {}

    public static String render(PageContext ctx) {
        return HtmlBuilder.create()
            .doctype()
            .html(b -> b
                .head(h -> h
                    .meta("charset", "UTF-8")
                    .meta("name", "viewport", "content", "width=device-width, initial-scale=1.0")
                    .title(ctx.getMessage("contact.title"))
                    .link("https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.rtl.min.css", "stylesheet")
                    .link("https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700&display=swap", "stylesheet")
                    .style(s -> LayoutRenderer.renderStyles(s, ctx))
                )
                .body(body -> body
                    .nav(nav -> LayoutRenderer.renderNavbar(nav, ctx))
                    .div(main -> main
                        .div(container -> container
                            .h1(ctx.getMessage("contact.title")).attr("class", "page-title")
                            .p(ctx.getMessage("contact.description"))
                            .form("/contact", "post", form -> form
                                .div(group -> group
                                    .label(ctx.getMessage("contact.name")).attr("for", "name")
                                    .input("text", "name", "id", "name", "class", "form-control", "required", "required")
                                ).attr("class", "mb-3")
                                .div(group -> group
                                    .label(ctx.getMessage("contact.email")).attr("for", "email")
                                    .input("email", "email", "id", "email", "class", "form-control", "required", "required")
                                ).attr("class", "mb-3")
                                .div(group -> group
                                    .label(ctx.getMessage("contact.subject")).attr("for", "subject")
                                    .input("text", "subject", "id", "subject", "class", "form-control", "required", "required")
                                ).attr("class", "mb-3")
                                .div(group -> group
                                    .label(ctx.getMessage("contact.message")).attr("for", "message")
                                    .textarea(t -> t.text("")).attr("name", "message").attr("id", "message").attr("class", "form-control").attr("rows", "5").attr("required", "required")
                                ).attr("class", "mb-3")
                                .button(btn -> btn.text(ctx.getMessage("contact.submit"))).attr("type", "submit").attr("class", "btn btn-primary")
                            ).attr("class", "contact-form")
                        ).attr("class", "container py-5")
                    )
                    .footer(f -> LayoutRenderer.renderFooter(f, ctx))
                    .script(s -> LayoutRenderer.renderScripts(s, ctx))
                )
            )
            .build();
    }
}