package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;
import java.util.List;
import java.util.Map;

public final class OutputsPageRenderer {
    private OutputsPageRenderer() {}

    @SuppressWarnings("unchecked")
    public static String render(PageContext ctx) {
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) ctx.attr("outputs");
        Integer currentPage = (Integer) ctx.attr("currentPage");
        Integer totalPages = (Integer) ctx.attr("totalPages");
        String filterTab = (String) ctx.attr("filterTab");
        String error = (String) ctx.attr("error");
        String success = (String) ctx.attr("success");
        String bookLanguage = (String) ctx.attr("bookLanguage");

        return PageRenderer.renderPage(ctx, body -> {
            body.nav(nav -> LayoutRenderer.renderNavbar(nav, ctx));
            body.div(main -> main
                .div(container -> container
                    .nav(nav2 -> nav2
                        .ol(ol -> ol
                            .attr("class", "breadcrumb")
                            .li(li -> li
                                .a("/", a -> a.text("Home"))
                            ).attr("class", "breadcrumb-item")
                            .li(li -> li.text("Outputs"))
                            .attr("class", "breadcrumb-item active")
                        )
                        .attr("aria-label", "breadcrumb")
                    )
                    .h2(ctx.getMessage("outputs.title"))
                    .p(ctx.getMessage("outputs.description"))
                    .raw(error != null ? "<div class=\"alert alert-danger\">" + escapeHtml(error) + "</div>" : "")
                    .raw(success != null ? "<div class=\"alert alert-success\">" + escapeHtml(success) + "</div>" : "")
                    .div(tableWrap -> tableWrap
                        .table(table -> table
                            .attr("class", "table table-striped table-hover")
                            .thead(thead -> thead
                                .tr(tr -> tr
                                    .th(ctx.getMessage("outputs.column.id"))
                                    .th(ctx.getMessage("outputs.column.tab"))
                                    .th(ctx.getMessage("outputs.column.created"))
                                    .th(ctx.getMessage("outputs.column.actions"))
                                )
                            )
                            .tbody(tbody -> {
                                if (outputs == null || outputs.isEmpty()) {
                                    tbody.tr(tr -> tr
                                        .td(td -> td
                                            .attr("colspan", "4")
                                            .attr("class", "text-center text-muted")
                                            .text(ctx.getMessage("outputs.noData"))
                                        )
                                    );
                                } else {
                                    for (Map<String, Object> output : outputs) {
                                        tbody.tr(tr -> tr
                                            .td(td -> td.text(String.valueOf(output.get("id"))))
                                            .td(td -> td.text(String.valueOf(output.get("tabUsed"))))
                                            .td(td -> td.text(String.valueOf(output.get("createdAt"))))
                                            .td(td -> td
                                                .form("/outputs/" + output.get("id") + "/delete", "post", f -> f
                                                    .input("hidden", ctx.csrfParameterName(), "value", ctx.csrfToken())
                                                    .button(btn -> btn.text(ctx.getMessage("outputs.delete"))).attr("type", "submit").attr("class", "btn btn-sm btn-danger")
                                                )
                                            )
                                        );
                                    }
                                }
                            })
                        )
                    )
                    .raw(pagination(currentPage, totalPages, filterTab))
                )
            )
            .footer(f -> LayoutRenderer.renderFooter(f, ctx));
            LayoutRenderer.renderScripts(body, ctx);
        });
    }

    private static String pagination(Integer currentPage, Integer totalPages, String filterTab) {
        if (totalPages == null || totalPages <= 1) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<nav aria-label=\"Page navigation\"><ul class=\"pagination\">");
        int cp = currentPage != null ? currentPage : 0;
        String tabParam = filterTab != null && !filterTab.isBlank() ? "&tab=" + filterTab : "";
        sb.append("<li class=\"page-item ").append(cp <= 0 ? "disabled" : "").append("\"><a class=\"page-link\" href=\"/outputs?page=").append(cp - 1).append(tabParam).append("\">Previous</a></li>");
        for (int i = 0; i < totalPages; i++) {
            sb.append("<li class=\"page-item ").append(i == cp ? "active" : "").append("\"><a class=\"page-link\" href=\"/outputs?page=").append(i).append(tabParam).append("\">").append(i + 1).append("</a></li>");
        }
        sb.append("<li class=\"page-item ").append(cp >= totalPages - 1 ? "disabled" : "").append("\"><a class=\"page-link\" href=\"/outputs?page=").append(cp + 1).append(tabParam).append("\">Next</a></li>");
        sb.append("</ul></nav>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
