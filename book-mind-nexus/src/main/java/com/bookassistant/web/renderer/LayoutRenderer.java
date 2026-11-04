package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public final class LayoutRenderer {
    private LayoutRenderer() {}

    public static void renderLayout(HtmlBuilder b, PageContext ctx, Consumer<HtmlBuilder> content) {
        b.header(h -> renderNavbar(h, ctx))
            .main(m -> content.accept(m))
            .footer(f -> renderFooter(f, ctx))
            .script(s -> renderScripts(s, ctx));
    }

    public static void renderNavbar(HtmlBuilder h, PageContext ctx) {
        h.div(n -> n
            .attr("class", "container")
            .div(nav -> nav
                .attr("class", "navbar navbar-expand-lg navbar-dark py-2")
                .a("/", a -> a
                    .span("🧠").attr("class", "logo-icon")
                    .span(ctx.getMessage("home.brand"))
                ).attr("class", "navbar-brand")
                .button(btn -> btn
                    .attr("class", "navbar-toggler")
                    .attr("type", "button")
                    .attr("data-bs-toggle", "collapse")
                    .attr("data-bs-target", "#mainNav")
                    .span("").attr("class", "navbar-toggler-icon")
                )
                .div(collapse -> {
                    collapse.attr("class", "collapse navbar-collapse").attr("id", "mainNav");

                    if (ctx.isLoggedIn()) {
                        List<String> tabs = List.of("ask", "insights", "study", "sync", "responses");
                        String[] labels = {
                            ctx.getMessage("app.ask"),
                            ctx.getMessage("app.insights"),
                            ctx.getMessage("app.lab"),
                            ctx.getMessage("app.sync"),
                            "Responses"
                        };
                        collapse.ul(ul -> {
                            ul.attr("class", "navbar-nav me-auto mb-2 mb-lg-0 nav-pills");
                            for (int i = 0; i < tabs.size(); i++) {
                                final int idx = i;
                                String tab = tabs.get(i);
                                ul.li(li -> li
                                    .button(btn -> btn
                                        .attr("class", "nav-link" + (idx == 0 ? " active" : ""))
                                        .attr("onclick", "switchOuterTab(this, '" + tab + "')")
                                        .span(labels[idx])
                                    ).attr("class", "nav-item")
                                );
                            }
                        });
                    }

                    collapse.div(d -> {
                        if (ctx.isLoggedIn()) {
                            d.span(ctx.getMessage("navbar.welcome") + " " + ctx.userEmail())
                                .attr("class", "text-light small");
                            d.a("/accounts", a -> a.text(ctx.getMessage("navbar.accounts")))
                                .attr("class", "btn btn-outline-nova btn-sm");
                            d.a("/outputs", a -> a.text(ctx.getMessage("navbar.outputs")))
                                .attr("class", "btn btn-outline-nova btn-sm");
                            d.form("/logout", "post", f -> f
                                .input("hidden", ctx.csrfParameterName(), "value", ctx.csrfToken())
                                .button(btn -> btn.text(ctx.getMessage("navbar.logout")))
                                .attr("class", "nav-cta-secondary")
                                .attr("type", "submit")
                            );
                        } else {
                            d.a("/login", a -> a.text(ctx.getMessage("navbar.login")))
                                .attr("class", "btn btn-nova btn-sm");
                        }
                        renderLangSwitch(d, ctx);
                        if (ctx.isLoggedIn()) {
                            renderExtractionModeSelect(d, ctx);
                        }
                    }).attr("class", "d-flex align-items-center gap-2");
                })
            )
        );
    }

    public static void renderLangSwitch(HtmlBuilder d, PageContext ctx) {
        d.select(s -> {
            s.option("", ctx.getMessage("navbar.language"), true);
            String[] langs = {"ar", "en", "es", "es-MX", "fr", "de", "de-AT", "it", "nl", "pt", "ru", "uk", "af", "zh", "ja"};
            for (String lang : langs) {
                s.option(lang, ctx.getMessage("lang." + lang), false);
            }
        }).attr("class", "form-select form-select-sm lang-switch")
          .attr("onchange", "if(this.value)window.location.href='/language/switch?lang='+this.value+'&redirect=/app'");
    }

    private static void renderExtractionModeSelect(HtmlBuilder d, PageContext ctx) {
        d.select(s -> {
            s.option("", ctx.getMessage("navbar.language"), true);
            s.option("pix2text", "Pix2Text", "pix2text".equals(ctx.extractionMode()));
            s.option("text-layer", "PDFBox Text", "text-layer".equals(ctx.extractionMode()));
            s.option("model-only", ctx.getMessage("extraction.mode.model.only"), "model-only".equals(ctx.extractionMode()));
        }).attr("id", "extractionModeSelect")
          .attr("class", "form-select form-select-sm lang-switch")
          .attr("onchange", "updateExtractionMode(this.value)");
    }

    public static void renderFooter(HtmlBuilder f, PageContext ctx) {
        f.p(p -> p
            .raw("🧠 ")
            .span(ctx.getMessage("home.brand"))
            .raw(" — ")
            .span(ctx.getMessage("home.footer.powered"))
        ).attr("class", "mb-0");
        f.p(p -> p
            .a("/", a -> a.text(ctx.getMessage("navbar.home")))
            .raw(" · ")
            .a("/about", a -> a.text(ctx.getMessage("navbar.about")))
            .raw(" · ")
            .a("/contact", a -> a.text(ctx.getMessage("navbar.contact")))
        ).attr("class", "mb-0");
    }

    public static void renderScripts(HtmlBuilder s, PageContext ctx) {
        s.attr("src", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js");
        s.raw("\n");
        s.attr("src", "/js/collapsible.js");
        s.raw("\n");
        s.attr("src", "https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.js");
        s.raw("\n");
        s.attr("src", "/js/ui-audit.js");
        s.attr("defer", "defer");
    }
}