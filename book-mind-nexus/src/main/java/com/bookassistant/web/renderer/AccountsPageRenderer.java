package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;
import java.util.List;
import java.util.Map;

public final class AccountsPageRenderer {
    private AccountsPageRenderer() {}

    @SuppressWarnings("unchecked")
    public static String render(PageContext ctx) {
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) ctx.attr("accounts");
        List<Map<String, Object>> notionAccounts = (List<Map<String, Object>>) ctx.attr("notionAccounts");
        boolean hasNotionAccount = (Boolean) ctx.attr("hasNotionAccount");
        boolean notionHasToken = (Boolean) ctx.attr("notionHasToken");
        long syncCount = (Long) ctx.attr("syncCount");
        String syncStatus = (String) ctx.attr("syncStatus");
        String error = (String) ctx.attr("error");

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
                            .li(li -> li.text("Accounts"))
                            .attr("class", "breadcrumb-item active")
                        )
                        .attr("aria-label", "breadcrumb")
                    )
                    .h2(ctx.getMessage("accounts.title"))
                    .p(ctx.getMessage("accounts.description"))
                    .raw(error != null ? "<div class=\"alert alert-danger\">" + escapeHtml(error) + "</div>" : "")

                    .div(section -> section
                        .h3(ctx.getMessage("accounts.linkedAccounts.title"))
                        .p(ctx.getMessage("accounts.linkedAccounts.description"))
                        .raw(accounts == null || accounts.isEmpty()
                            ? "<div class=\"alert alert-info\">" + ctx.getMessage("accounts.linkedAccounts.none") + "</div>"
                            : "")
                        .table(table -> table
                            .attr("class", "table table-striped table-hover")
                            .thead(thead -> thead
                                .tr(tr -> tr
                                    .th(ctx.getMessage("accounts.column.platform"))
                                    .th(ctx.getMessage("accounts.column.accountName"))
                                    .th(ctx.getMessage("accounts.column.actions"))
                                )
                            )
                            .tbody(tbody -> {
                                if (accounts != null) {
                                    for (Map<String, Object> acc : accounts) {
                                        tbody.tr(tr -> tr
                                            .td(td -> td.text(String.valueOf(acc.get("platform"))))
                                            .td(td -> td.text(String.valueOf(acc.get("accountName"))))
                                            .td(td -> td
                                                .form("/accounts/unlink", "post", f -> f
                                                    .input("hidden", ctx.csrfParameterName(), "value", ctx.csrfToken())
                                                    .input("hidden", "platform", "value", String.valueOf(acc.get("platform")))
                                                    .button(btn -> btn.text(ctx.getMessage("accounts.unlink"))).attr("type", "submit").attr("class", "btn btn-sm btn-outline-danger")
                                                )
                                            )
                                        );
                                    }
                                }
                            })
                        )
                    )

                    .div(section -> section
                        .h3(ctx.getMessage("accounts.notion.title"))
                        .p(ctx.getMessage("accounts.notion.description"))
                        .raw(hasNotionAccount
                            ? (notionHasToken
                                ? "<div class=\"alert alert-success\">" + ctx.getMessage("accounts.notion.connected") + "</div>"
                                : "<div class=\"alert alert-warning\">" + ctx.getMessage("accounts.notion.noToken") + "</div>")
                            : "<div class=\"alert alert-info\">" + ctx.getMessage("accounts.notion.notConnected") + "</div>")
                    )

                    .div(section -> section
                        .h3(ctx.getMessage("accounts.sync.title"))
                        .p(ctx.getMessage("accounts.sync.description"))
                        .p(syncStatus)
                        .form("/accounts/sync", "post", f -> f
                            .input("hidden", ctx.csrfParameterName(), "value", ctx.csrfToken())
                            .button(btn -> btn.text(ctx.getMessage("accounts.sync.button"))).attr("type", "submit").attr("class", "btn btn-primary")
                        )
                        .p(syncCount + " notes synchronized", "class", "text-muted small")
                    )
                )
            )
            .footer(f -> LayoutRenderer.renderFooter(f, ctx));
            LayoutRenderer.renderScripts(body, ctx);
        });
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
