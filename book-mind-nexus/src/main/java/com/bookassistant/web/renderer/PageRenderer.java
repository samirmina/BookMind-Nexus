package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;
import java.util.function.Consumer;

public interface PageRenderer {
    String render(PageContext ctx);

    static String renderPage(PageContext ctx, Consumer<HtmlBuilder> content) {
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
                .body(content)
            )
            .build();
    }
}