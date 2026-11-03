package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class HomePageRenderer {
    private HomePageRenderer() {}

    public static String render(PageContext ctx) {
        return HtmlBuilder.create()
            .doctype()
            .html(b -> b
                .head(h -> h
                    .meta("charset", "UTF-8")
                    .meta("name", "viewport", "content", "width=device-width, initial-scale=1.0")
                    .title(ctx.getMessage("home.title"))
                    .link("https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.rtl.min.css", "stylesheet")
                    .link("https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700&display=swap", "stylesheet")
                    .link("/styles.css", "stylesheet")
                )
                .body(body -> body
                    .nav(nav -> renderNavbar(nav, ctx))
                    .section(s -> renderHero(s, ctx))
                    .section(s -> renderFeatures(s, ctx))
                    .section(s -> renderHowItWorks(s, ctx))
                    .footer(f -> LayoutRenderer.renderFooter(f, ctx))
                    .script(s -> renderScripts(s, ctx))
                )
            )
            .build();
    }

    private static void renderNavbar(HtmlBuilder nav, PageContext ctx) {
        nav.div(inner -> inner
            .a("/", a -> a
                .span("🧠").attr("class", "brand-icon")
                .span(ctx.getMessage("home.brand"))
            ).attr("class", "brand")
            .div(links -> {
                links.a("#features", a -> a.text(ctx.getMessage("home.features"))).attr("class", "nav-link");
                links.a("#how-it-works", a -> a.text(ctx.getMessage("home.howItWorks"))).attr("class", "nav-link");
                links.a("/contact", a -> a.text(ctx.getMessage("home.contact"))).attr("class", "nav-link");
                if (!ctx.isLoggedIn()) {
                    links.a("/login", a -> a.text(ctx.getMessage("navbar.login"))).attr("class", "nav-cta");
                    links.a("/register", a -> a.text(ctx.getMessage("register.title"))).attr("class", "nav-cta-secondary");
                } else {
                    links.a("/app", a -> a.text(ctx.getMessage("navbar.app"))).attr("class", "nav-cta");
                    links.a("/outputs", a -> a.raw("📋 " + ctx.getMessage("navbar.outputs"))).attr("class", "nav-cta-secondary");
                    links.form("/logout", "post", f -> f
                        .button(btn -> btn.text(ctx.getMessage("navbar.logout")))
                            .attr("class", "nav-cta-secondary")
                            .attr("type", "submit")
                    ).attr("class", "d-inline");
                }
                LayoutRenderer.renderLangSwitch(links, ctx);
            }).attr("class", "nav-links")
        ).attr("class", "navbar-inner");
    }

    private static void renderHero(HtmlBuilder section, PageContext ctx) {
        section.div(inner -> inner
            .div(d -> d.text(ctx.getMessage("home.hero.ar"))).attr("class", "hero-ar")
            .div(d -> d.text(ctx.getMessage("home.hero.en"))).attr("class", "hero-en")
            .h1(ctx.getMessage("home.hero.title")).attr("class", "hero-title")
            .div(actions -> {
                if (!ctx.isLoggedIn()) {
                    actions.a("/login", a -> a.text(ctx.getMessage("home.hero.cta"))).attr("class", "btn-primary-hero");
                } else {
                    actions.a("/app", a -> a.text(ctx.getMessage("home.hero.cta"))).attr("class", "btn-primary-hero");
                }
                actions.a("#how-it-works", a -> a.text(ctx.getMessage("home.hero.learnMore"))).attr("class", "btn-secondary-hero");
            }).attr("class", "hero-actions")
        ).attr("class", "hero");
    }

    private static void renderFeatures(HtmlBuilder section, PageContext ctx) {
        section.div(inner -> inner
            .h2(ctx.getMessage("home.features.title")).attr("class", "section-title")
            .div(grid -> {
                String[] icons = {"💬", "📊", "🧪", "🔗", "🧩", "🌐"};
                String[] keys = {"ask", "insights", "lab", "sync", "images", "bilingual"};
                for (int i = 0; i < keys.length; i++) {
                    final int idx = i;
                    grid.div(card -> card
                        .span(icons[idx]).attr("class", "feature-icon")
                        .h3(ctx.getMessage("home.features." + keys[idx]))
                        .p(ctx.getMessage("home.features." + keys[idx] + ".desc"))
                    ).attr("class", "feature-card");
                }
            }).attr("class", "features-grid")
        ).attr("class", "section-inner");
    }

    private static void renderHowItWorks(HtmlBuilder section, PageContext ctx) {
        section.div(inner -> inner
            .h2(ctx.getMessage("home.howItWorks.title")).attr("class", "section-title")
            .div(grid -> {
                for (int i = 1; i <= 4; i++) {
                    final int idx = i;
                    grid.div(step -> step
                        .div(String.valueOf(idx)).attr("class", "step-num")
                        .h4(ctx.getMessage("home.step" + idx + ".title"))
                        .p(ctx.getMessage("home.step" + idx + ".desc"))
                    ).attr("class", "step");
                }
            }).attr("class", "steps-grid")
        ).attr("class", "section-inner");
    }

    private static void renderScripts(HtmlBuilder s, PageContext ctx) {
        s.attr("src", "/js/ui-audit.js");
        s.attr("defer", "defer");
    }
}