package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class AppPageRenderer {
    private AppPageRenderer() {}

    public static String render(PageContext ctx) {
        return HtmlBuilder.create()
            .doctype()
            .html(b -> b
                .head(h -> h
                    .meta("charset", "UTF-8")
                    .meta("name", "viewport", "content", "width=device-width, initial-scale=1.0")
                    .title(ctx.getMessage("app.title"))
                    .link("https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.rtl.min.css", "stylesheet")
                    .link("https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap", "stylesheet")
                    .style(s -> LayoutRenderer.renderStyles(s, ctx))
                    .link("https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.css", "stylesheet")
                )
                .body(body -> {
                    body
                        .nav(nav -> LayoutRenderer.renderNavbar(nav, ctx))
                        .div(main -> main
                            .div(container -> container
                                .h1(ctx.getMessage("app.title")).attr("class", "page-title mb-4")
                                .p(ctx.getMessage("app.qa.title")).attr("class", "lead")
                                .div(id -> id.attr("id", "app-root").attr("class", "app-container")
                                    .raw("<!-- Single-page application content rendered client-side -->")
                                )
                            ).attr("class", "container py-4")
                        )
                        .footer(f -> LayoutRenderer.renderFooter(f, ctx));
                    LayoutRenderer.renderScripts(body, ctx);
                })
            )
            .build();
    }
}