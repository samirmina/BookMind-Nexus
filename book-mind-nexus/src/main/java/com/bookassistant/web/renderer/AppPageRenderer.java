package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;

public final class AppPageRenderer {
    private AppPageRenderer() {}

    public static String render(PageContext ctx) {
        return PageRenderer.renderPage(ctx, body -> {
            body.nav(nav -> LayoutRenderer.renderNavbar(nav, ctx));
            body.div(main -> main
                .div(container -> container
                    .h1(ctx.getMessage("app.title")).attr("class", "page-title mb-4")
                    .p(ctx.getMessage("app.qa.title")).attr("class", "lead")

                    // Error / success alerts
                    .raw(renderAlert(ctx))

                    // QA Section
                    .raw(renderQaSection(ctx))

                    // Analysis Section (Reality Check, Concept Map, Risk Flags)
                    .raw(renderAnalysisSection(ctx))

                    // Search Section
                    .raw(renderSearchSection(ctx))

                    // Summary Section
                    .raw(renderSummarySection(ctx))

                    // Comprehension Section
                    .raw(renderComprehensionSection(ctx))

                    // Action Plan Section
                    .raw(renderActionPlanSection(ctx))

                    // Empty app root for future client-side features
                    .div(id -> id.attr("id", "app-root").attr("class", "app-container mt-4")
                        .raw("<!-- Application content area -->")
                    )
                ).attr("class", "container py-4")
            )
            .footer(f -> LayoutRenderer.renderFooter(f, ctx));
            LayoutRenderer.renderScripts(body, ctx);
        });
    }

    // ============================================================
    // Section renderers
    // ============================================================

    private static String renderAlert(PageContext ctx) {
        String error = (String) ctx.attr("error");
        String success = (String) ctx.attr("success");
        StringBuilder sb = new StringBuilder();
        if (error != null && !error.isEmpty()) {
            sb.append("<div class=\"alert alert-danger alert-dismissible fade show\">")
              .append(escapeHtml(error))
              .append("<button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\"></button></div>");
        }
        if (success != null && !success.isEmpty()) {
            sb.append("<div class=\"alert alert-success alert-dismissible fade show\">")
              .append(escapeHtml(success))
              .append("<button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\"></button></div>");
        }
        return sb.toString();
    }

    private static String renderQaSection(PageContext ctx) {
        String qa = (String) ctx.attr("qa");
        if (qa == null || qa.isEmpty()) return "";
        String dir = (String) ctx.attr("qaDirection");
        String lang = (String) ctx.attr("qaLanguage");
        String htmlAttrs = (String) ctx.attr("qaHtmlAttributes");
        String inlineStyle = (String) ctx.attr("qaInlineStyle");
        return "<div class=\"card mb-4\">" +
            "<div class=\"card-header\">" + ctx.getMessage("qa.title") + "</div>" +
            "<div class=\"card-body\" " + (dir != null ? "dir=\"" + dir + "\"" : "") +
            " " + (htmlAttrs != null ? htmlAttrs : "") +
            " " + (inlineStyle != null ? "style=\"" + inlineStyle + "\"" : "") + ">" +
            qa + "</div></div>";
    }

    private static String renderAnalysisSection(PageContext ctx) {
        StringBuilder sb = new StringBuilder();

        String realityCheck = (String) ctx.attr("realityCheck");
        if (realityCheck != null && !realityCheck.isEmpty()) {
            sb.append(renderAnalysisCard(
                ctx.getMessage("analysis.realityCheck.title"),
                realityCheck,
                (String) ctx.attr("realityCheckDirection"),
                (String) ctx.attr("realityCheckLanguage"),
                (String) ctx.attr("realityCheckHtmlAttributes"),
                (String) ctx.attr("realityCheckInlineStyle")
            ));
        }

        String conceptMap = (String) ctx.attr("conceptMap");
        if (conceptMap != null && !conceptMap.isEmpty()) {
            sb.append(renderAnalysisCard(
                ctx.getMessage("analysis.conceptMap.title"),
                conceptMap,
                (String) ctx.attr("conceptMapDirection"),
                (String) ctx.attr("conceptMapLanguage"),
                (String) ctx.attr("conceptMapHtmlAttributes"),
                (String) ctx.attr("conceptMapInlineStyle")
            ));
        }

        String riskFlags = (String) ctx.attr("riskFlags");
        if (riskFlags != null && !riskFlags.isEmpty()) {
            sb.append(renderAnalysisCard(
                ctx.getMessage("analysis.riskFlags.title"),
                riskFlags,
                (String) ctx.attr("riskFlagsDirection"),
                (String) ctx.attr("riskFlagsLanguage"),
                (String) ctx.attr("riskFlagsHtmlAttributes"),
                (String) ctx.attr("riskFlagsInlineStyle")
            ));
        }

        return sb.toString();
    }

    private static String renderAnalysisCard(String title, String content, String dir, String lang, String htmlAttrs, String inlineStyle) {
        return "<div class=\"card mb-4\">" +
            "<div class=\"card-header\">" + title + "</div>" +
            "<div class=\"card-body\" " + (dir != null ? "dir=\"" + dir + "\"" : "") +
            " " + (htmlAttrs != null ? htmlAttrs : "") +
            " " + (inlineStyle != null ? "style=\"" + inlineStyle + "\"" : "") + ">" +
            content + "</div></div>";
    }

    private static String renderSearchSection(PageContext ctx) {
        String semanticSearch = (String) ctx.attr("semanticSearch");
        if (semanticSearch == null || semanticSearch.isEmpty()) return "";
        return "<div class=\"card mb-4\">" +
            "<div class=\"card-header\">" + ctx.getMessage("search.title") + "</div>" +
            "<div class=\"card-body\">" + semanticSearch + "</div></div>";
    }

    private static String renderSummarySection(PageContext ctx) {
        StringBuilder sb = new StringBuilder();

        String summaryGeneral = (String) ctx.attr("summaryGeneral");
        if (summaryGeneral != null && !summaryGeneral.isEmpty()) {
            sb.append("<div class=\"card mb-4\">" +
                "<div class=\"card-header\">" + ctx.getMessage("summary.title.general") + "</div>" +
                "<div class=\"card-body\" " +
                (ctx.attr("summaryGeneralDirection") != null ? "dir=\"" + ctx.attr("summaryGeneralDirection") + "\"" : "") +
                ">" + summaryGeneral + "</div></div>");
        }

        String summaryPages = (String) ctx.attr("summaryPages");
        if (summaryPages != null && !summaryPages.isEmpty()) {
            sb.append("<div class=\"card mb-4\">" +
                "<div class=\"card-header\">" + ctx.getMessage("summary.title.pages") + "</div>" +
                "<div class=\"card-body\" " +
                (ctx.attr("summaryPagesDirection") != null ? "dir=\"" + ctx.attr("summaryPagesDirection") + "\"" : "") +
                ">" + summaryPages + "</div></div>");
        }

        return sb.toString();
    }

    private static String renderComprehensionSection(PageContext ctx) {
        String comprehension = (String) ctx.attr("comprehension");
        if (comprehension == null || comprehension.isEmpty()) return "";
        String dir = (String) ctx.attr("comprehensionDirection");
        return "<div class=\"card mb-4\">" +
            "<div class=\"card-header\">" + ctx.getMessage("comprehension.title") + "</div>" +
            "<div class=\"card-body\" " + (dir != null ? "dir=\"" + dir + "\"" : "") + ">" +
            comprehension + "</div></div>";
    }

    private static String renderActionPlanSection(PageContext ctx) {
        String actionPlan = (String) ctx.attr("actionPlan");
        if (actionPlan == null || actionPlan.isEmpty()) return "";
        return "<div class=\"card mb-4\">" +
            "<div class=\"card-header\">" + ctx.getMessage("plan.title") + "</div>" +
            "<div class=\"card-body\">" + actionPlan + "</div></div>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
