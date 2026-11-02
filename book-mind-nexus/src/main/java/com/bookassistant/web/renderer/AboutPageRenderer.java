package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class AboutPageRenderer {
    private AboutPageRenderer() {}

    public static String render(PageContext ctx) {
        return HtmlBuilder.create()
            .doctype()
            .html(b -> b
                .head(h -> h
                    .meta("charset", "UTF-8")
                    .meta("name", "viewport", "content", "width=device-width, initial-scale=1.0")
                    .title(ctx.getMessage("about.title"))
                    .link("https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.rtl.min.css", "stylesheet")
                    .link("https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700&display=swap", "stylesheet")
                    .link("/styles.css", "stylesheet")
                )
                .body(body -> body
                    .nav(nav -> LayoutRenderer.renderNavbar(nav, ctx))
                    .div(main -> main
                        .div(container -> container
                            .h1(ctx.getMessage("about.title")).attr("class", "page-title")
                            .p(ctx.getMessage("about.description"))
                            .h2(ctx.getMessage("about.philosophy.title"))
                            .p(ctx.getMessage("about.philosophy.body"))
                            .h2(ctx.getMessage("about.features.title"))
                            .ul(ul -> {
                                ul.li(ctx.getMessage("about.features.extraction"));
                                ul.li(ctx.getMessage("about.features.ai"));
                                ul.li(ctx.getMessage("about.features.privacy"));
                                ul.li(ctx.getMessage("about.features.offline"));
                            })
                            .h2(ctx.getMessage("about.tech.title"))
                            .p(ctx.getMessage("about.tech.body"))
                        ).attr("class", "container py-5")
                    )
                    .footer(f -> LayoutRenderer.renderFooter(f, ctx))
                    .script(s -> LayoutRenderer.renderScripts(s, ctx))
                )
            )
            .build();
    }
}