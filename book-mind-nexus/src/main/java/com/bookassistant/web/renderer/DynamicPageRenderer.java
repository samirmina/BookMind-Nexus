package com.bookassistant.web.renderer;

import java.util.List;
import java.util.Map;

/**
 * Dynamic page renderer that assembles pages from component renderers based on
 * model attributes. Instead of hardcoded per-page renderer classes, this reads
 * the view name as the page identifier and renders the appropriate components.
 *
 * Controllers simply set model attributes and return any view name.
 * The renderer uses the view name as the page identifier and delegates feature
 * rendering to registered ComponentRenderer instances.
 *
 * Page layout:
 *   - Navbar (always rendered)
 *   - Alerts (error/success from model)
 *   - Main container with page-specific content
 *   - Footer (always rendered)
 *   - Bootstrap + KaTeX scripts
 *
 * Feature rendering (for "app" page):
 *   - Each model attribute matching a registered feature type is rendered
 *     by its ComponentRenderer
 *   - Features: qa, summary, analysis, search, comprehension, actionPlan
 */
public class DynamicPageRenderer {

    private final ComponentRegistry registry;

    public DynamicPageRenderer(ComponentRegistry registry) {
        this.registry = registry;
    }

    public String render(PageContext ctx, Map<String, Object> model, String viewName) {
        String page = viewName;
        String error = (String) model.get("error");
        String success = (String) model.get("success");

        StringBuilder sb = new StringBuilder();

        // DOCTYPE and HTML wrapper
        sb.append("<!DOCTYPE html>\n<html lang=\"").append(ctx.lang()).append("\">\n<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("    <title>").append(pageTitle(page, ctx)).append("</title>\n");
        sb.append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.rtl.min.css\" rel=\"stylesheet\">\n");
        sb.append("    <link href=\"https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">\n");
        sb.append("    <style>\n");
        HtmlBuilder styleBuilder = HtmlBuilder.create();
        LayoutRenderer.renderStyles(styleBuilder, ctx);
        sb.append(styleBuilder.build());
        sb.append("    </style>\n");
        sb.append("</head>\n<body>\n");

        // Navbar
        sb.append(renderNavbar(ctx, model));

        // Alerts
        if (error != null && !error.isEmpty()) {
            sb.append(HtmlComponents.alert("danger", error, true));
        }
        if (success != null && !success.isEmpty()) {
            sb.append(HtmlComponents.alert("success", success, true));
        }

        // Main container
        sb.append("<div class=\"container py-4\">\n");

        // Page-specific content
        switch (page) {
            case "home":
                sb.append(renderHome(ctx, model));
                break;
            case "login":
                sb.append(renderLogin(ctx, model));
                break;
            case "register":
                sb.append(renderRegister(ctx, model));
                break;
            case "about":
                sb.append(renderAbout(ctx, model));
                break;
            case "contact":
                sb.append(renderContact(ctx, model));
                break;
            case "app":
                sb.append(renderApp(ctx, model));
                break;
            case "outputs/index":
                sb.append(renderOutputs(ctx, model));
                break;
            case "accounts/index":
                sb.append(renderAccounts(ctx, model));
                break;
            default:
                sb.append("<div class=\"alert alert-warning\">Unknown page: ")
                  .append(HtmlComponents.escapeHtml(page)).append("</div>");
        }

        sb.append("</div>\n"); // close container

        // Footer
        sb.append("<footer class=\"mt-5 py-4 border-top\">\n");
        sb.append("    <div class=\"container\">\n");
        sb.append("        <p class=\"mb-0 text-muted\">🧠 BookMind Nexus — Powered by AI</p>\n");
        sb.append("        <p class=\"mb-0 text-muted\"><a href=\"/\">Home</a> · <a href=\"/about\">About</a> · <a href=\"/contact\">Contact</a></p>\n");
        sb.append("    </div>\n");
        sb.append("</footer>\n");

        // Scripts
        sb.append("    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js\"></script>\n");
        sb.append("    <script src=\"https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.js\"></script>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    // ============================================================
    // Page renderers (static content pages)
    // ============================================================

    private String renderHome(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlComponents.section(ctx.getMessage("home.title")));
        sb.append("<div class=\"row\">\n");
        sb.append("    <div class=\"col-md-8\">\n");
        sb.append("        <div class=\"hero\" style=\"background:linear-gradient(135deg,var(--bs-primary),var(--bs-secondary));color:white;padding:3rem 1rem;border-radius:0.5rem;margin-bottom:2rem;\">\n");
        sb.append("            <h1 class=\"hero-title\">BookMind Nexus</h1>\n");
        sb.append("            <p class=\"lead\">One book. All your knowledge. Infinite understanding.</p>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"col-md-4\">\n");
        sb.append("        <div class=\"card\">\n");
        sb.append("            <div class=\"card-header\">").append(ctx.getMessage("home.quickActions")).append("</div>\n");
        sb.append("            <div class=\"card-body\">\n");
        sb.append("                <a href=\"/app\" class=\"btn btn-primary w-100 mb-2\">").append(ctx.getMessage("home.startAnalysis")).append("</a>\n");
        sb.append("                <a href=\"/about\" class=\"btn btn-outline-secondary w-100\">").append(ctx.getMessage("home.learnMore")).append("</a>\n");
        sb.append("            </div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private String renderLogin(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"row justify-content-center\">\n");
        sb.append("    <div class=\"col-md-6\">\n");
        sb.append("        <div class=\"card\">\n");
        sb.append("            <div class=\"card-header\">").append(ctx.getMessage("login.title")).append("</div>\n");
        sb.append("            <div class=\"card-body\">\n");
        sb.append(HtmlComponents.formStart("/login", "post", ctx.csrfParameterName(), ctx.csrfToken()));
        sb.append(HtmlComponents.input("email", "email", ctx.getMessage("login.email"), null, true));
        sb.append(HtmlComponents.input("password", "password", ctx.getMessage("login.password"), null, true));
        sb.append("<div class=\"mb-3 form-check\">\n");
        sb.append("    <input type=\"checkbox\" name=\"remember\" id=\"remember\" class=\"form-check-input\">\n");
        sb.append("    <label class=\"form-check-label\" for=\"remember\">").append(ctx.getMessage("login.remember")).append("</label>\n");
        sb.append("</div>\n");
        sb.append(HtmlComponents.button(ctx.getMessage("login.submit"), "submit", null, "btn-primary w-100"));
        sb.append(HtmlComponents.formEnd());
        sb.append("<div class=\"text-center mt-3\">\n");
        sb.append("    <a href=\"/register\" class=\"text-muted\">").append(ctx.getMessage("register.title")).append("</a>\n");
        sb.append("    <span> | </span>\n");
        sb.append("    <a href=\"/forgot-password\" class=\"text-muted\">").append(ctx.getMessage("login.forgot")).append("</a>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private String renderRegister(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"row justify-content-center\">\n");
        sb.append("    <div class=\"col-md-6\">\n");
        sb.append("        <div class=\"card\">\n");
        sb.append("            <div class=\"card-header\">").append(ctx.getMessage("register.title")).append("</div>\n");
        sb.append("            <div class=\"card-body\">\n");
        sb.append(HtmlComponents.formStart("/register", "post", ctx.csrfParameterName(), ctx.csrfToken()));
        sb.append(HtmlComponents.input("text", "name", ctx.getMessage("register.name"), null, false));
        sb.append(HtmlComponents.input("email", "email", ctx.getMessage("register.email"), null, true));
        sb.append(HtmlComponents.input("password", "password", ctx.getMessage("register.password"), null, true));
        sb.append(HtmlComponents.input("password", "confirm", ctx.getMessage("register.confirm"), null, true));
        sb.append(HtmlComponents.button(ctx.getMessage("register.submit"), "submit", null, "btn-primary w-100"));
        sb.append(HtmlComponents.formEnd());
        sb.append("<div class=\"text-center mt-3\">\n");
        sb.append("    <a href=\"/login\" class=\"text-muted\">").append(ctx.getMessage("login.title")).append("</a>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private String renderAbout(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append(HtmlComponents.section(ctx.getMessage("about.title")));
        sb.append("<p>").append(ctx.getMessage("about.description")).append("</p>\n");
        sb.append("<h3>").append(ctx.getMessage("about.philosophy.title")).append("</h3>\n");
        sb.append("<p>").append(ctx.getMessage("about.philosophy.body")).append("</p>\n");
        sb.append("<h3>").append(ctx.getMessage("about.features.title")).append("</h3>\n");
        sb.append("<ul>\n");
        sb.append("    <li>").append(ctx.getMessage("about.features.extraction")).append("</li>\n");
        sb.append("    <li>").append(ctx.getMessage("about.features.ai")).append("</li>\n");
        sb.append("    <li>").append(ctx.getMessage("about.features.privacy")).append("</li>\n");
        sb.append("    <li>").append(ctx.getMessage("about.features.offline")).append("</li>\n");
        sb.append("</ul>\n");
        sb.append("<h3>").append(ctx.getMessage("about.tech.title")).append("</h3>\n");
        sb.append("<p>").append(ctx.getMessage("about.tech.body")).append("</p>\n");
        return sb.toString();
    }

    private String renderContact(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(ctx.getMessage("contact.title")).append("</h2>\n");
        sb.append("<p>").append(ctx.getMessage("contact.description")).append("</p>\n");
        sb.append(HtmlComponents.formStart("/contact", "post", ctx.csrfParameterName(), ctx.csrfToken()));
        sb.append(HtmlComponents.input("text", "name", ctx.getMessage("contact.name"), null, true));
        sb.append(HtmlComponents.input("email", "email", ctx.getMessage("contact.email"), null, true));
        sb.append(HtmlComponents.input("text", "subject", ctx.getMessage("contact.subject"), null, true));
        sb.append("<div class=\"mb-3\">\n");
        sb.append("    <label class=\"form-label\">").append(ctx.getMessage("contact.message")).append("</label>\n");
        sb.append("    <textarea name=\"message\" id=\"message\" class=\"form-control\" rows=\"5\" required></textarea>\n");
        sb.append("</div>\n");
        sb.append(HtmlComponents.button(ctx.getMessage("contact.submit"), "submit", null, "btn-primary"));
        sb.append(HtmlComponents.formEnd());
        return sb.toString();
    }

    // ============================================================
    // Feature renderers (delegated to ComponentRegistry)
    // ============================================================

    private String renderApp(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>").append(ctx.getMessage("app.title")).append("</h1>\n");

        // Delegate each feature to its registered component renderer
        for (Map.Entry<String, ComponentRenderer> entry : registry.getAll().entrySet()) {
            String featureType = entry.getKey();
            Object featureData = model.get(featureType);
            if (featureData != null && !featureData.toString().isEmpty()) {
                sb.append(entry.getValue().render(ctx, model));
            }
        }

        return sb.toString();
    }

    private String renderOutputs(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(ctx.getMessage("outputs.title")).append("</h2>\n");
        sb.append("<p>").append(ctx.getMessage("outputs.description")).append("</p>\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) model.get("outputs");
        Integer currentPage = (Integer) model.get("currentPage");
        Integer totalPages = (Integer) model.get("totalPages");

        if (outputs == null || outputs.isEmpty()) {
            sb.append("<div class=\"alert alert-info\">").append(ctx.getMessage("outputs.noData")).append("</div>\n");
        } else {
            sb.append("<div class=\"table-responsive\"><table class=\"table table-striped table-hover\">\n");
            sb.append("<thead><tr>");
            sb.append("<th>").append(ctx.getMessage("outputs.column.id")).append("</th>");
            sb.append("<th>").append(ctx.getMessage("outputs.column.tab")).append("</th>");
            sb.append("<th>").append(ctx.getMessage("outputs.column.created")).append("</th>");
            sb.append("<th>").append(ctx.getMessage("outputs.column.actions")).append("</th>");
            sb.append("</tr></thead><tbody>\n");
            for (Map<String, Object> output : outputs) {
                sb.append("<tr>");
                sb.append("<td>").append(output.get("id")).append("</td>");
                sb.append("<td>").append(output.get("tabUsed")).append("</td>");
                sb.append("<td>").append(output.get("createdAt")).append("</td>");
                sb.append("<td>");
                sb.append("<form action=\"/outputs/").append(output.get("id")).append("/delete\" method=\"post\" style=\"display:inline;\">");
                sb.append("<input type=\"hidden\" name=\"").append(ctx.csrfParameterName()).append("\" value=\"").append(ctx.csrfToken()).append("\">");
                sb.append("<button class=\"btn btn-sm btn-outline-danger\">Delete</button>");
                sb.append("</form>");
                sb.append("</td>");
                sb.append("</tr>\n");
            }
            sb.append("</tbody></table></div>\n");
        }

        if (totalPages != null && totalPages > 1) {
            sb.append(renderPagination(currentPage, totalPages, (String) model.get("filterTab")));
        }

        return sb.toString();
    }

    private String renderAccounts(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(ctx.getMessage("accounts.title")).append("</h2>\n");
        sb.append("<p>").append(ctx.getMessage("accounts.description")).append("</p>\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) model.get("accounts");
        boolean hasNotion = (Boolean) model.get("hasNotionAccount");
        boolean notionToken = (Boolean) model.get("notionHasToken");
        long syncCount = (Long) model.get("syncCount");
        String syncStatus = (String) model.get("syncStatus");
        String error = (String) model.get("error");

        sb.append("<h3>").append(ctx.getMessage("accounts.linkedAccounts.title")).append("</h3>\n");
        sb.append("<p>").append(ctx.getMessage("accounts.linkedAccounts.description")).append("</p>\n");

        if (accounts == null || accounts.isEmpty()) {
            sb.append("<div class=\"alert alert-info\">").append(ctx.getMessage("accounts.linkedAccounts.none")).append("</div>\n");
        } else {
            sb.append("<table class=\"table table-striped table-hover\">\n");
            sb.append("<thead><tr>");
            sb.append("<th>").append(ctx.getMessage("accounts.column.platform")).append("</th>");
            sb.append("<th>").append(ctx.getMessage("accounts.column.accountName")).append("</th>");
            sb.append("<th>").append(ctx.getMessage("accounts.column.actions")).append("</th>");
            sb.append("</tr></thead><tbody>\n");
            for (Map<String, Object> acc : accounts) {
                sb.append("<tr>");
                sb.append("<td>").append(acc.get("platform")).append("</td>");
                sb.append("<td>").append(acc.get("accountName")).append("</td>");
                sb.append("<td>");
                sb.append("<form action=\"/accounts/unlink\" method=\"post\" style=\"display:inline;\">");
                sb.append("<input type=\"hidden\" name=\"").append(ctx.csrfParameterName()).append("\" value=\"").append(ctx.csrfToken()).append("\">");
                sb.append("<input type=\"hidden\" name=\"platform\" value=\"").append(acc.get("platform")).append("\">");
                sb.append("<button class=\"btn btn-sm btn-outline-danger\">").append(ctx.getMessage("accounts.unlink")).append("</button>");
                sb.append("</form>");
                sb.append("</td>");
                sb.append("</tr>\n");
            }
            sb.append("</tbody></table>\n");
        }

        sb.append("<h3>").append(ctx.getMessage("accounts.notion.title")).append("</h3>\n");
        sb.append("<p>").append(ctx.getMessage("accounts.notion.description")).append("</p>\n");
        if (hasNotion) {
            if (notionToken) {
                sb.append("<div class=\"alert alert-success\">").append(ctx.getMessage("accounts.notion.connected")).append("</div>\n");
            } else {
                sb.append("<div class=\"alert alert-warning\">").append(ctx.getMessage("accounts.notion.noToken")).append("</div>\n");
            }
        } else {
            sb.append("<div class=\"alert alert-info\">").append(ctx.getMessage("accounts.notion.notConnected")).append("</div>\n");
        }

        sb.append("<h3>").append(ctx.getMessage("accounts.sync.title")).append("</h3>\n");
        sb.append("<p>").append(ctx.getMessage("accounts.sync.description")).append("</p>\n");
        sb.append("<p>").append(syncStatus).append("</p>\n");
        sb.append(HtmlComponents.formStart("/accounts/sync", "post", ctx.csrfParameterName(), ctx.csrfToken()));
        sb.append(HtmlComponents.button(ctx.getMessage("accounts.sync.button"), "submit", null, "btn-primary"));
        sb.append(HtmlComponents.formEnd());
        sb.append("<p class=\"text-muted small\">").append(syncCount).append(" notes synchronized</p>\n");

        if (error != null && !error.isEmpty()) {
            sb.append(HtmlComponents.alert("danger", error, true));
        }

        return sb.toString();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String pageTitle(String page, PageContext ctx) {
        switch (page) {
            case "home": return ctx.getMessage("home.title");
            case "login": return ctx.getMessage("login.title");
            case "register": return ctx.getMessage("register.title");
            case "about": return ctx.getMessage("about.title");
            case "contact": return ctx.getMessage("contact.title");
            case "app": return ctx.getMessage("app.title");
            case "outputs/index": return ctx.getMessage("outputs.title");
            case "accounts/index": return ctx.getMessage("accounts.title");
            default: return "BookMind Nexus";
        }
    }

    private String renderNavbar(PageContext ctx, Map<String, Object> model) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"navbar navbar-expand-lg navbar-dark bg-dark mb-3\">\n");
        sb.append("    <div class=\"container-fluid\">\n");
        sb.append("        <a class=\"navbar-brand\" href=\"/\">🧠 BookMind Nexus</a>\n");
        sb.append("        <div class=\"collapse navbar-collapse\">\n");
        sb.append("            <ul class=\"navbar-nav me-auto mb-2 mb-lg-0\">\n");
        sb.append("                <li class=\"nav-item\"><a class=\"nav-link\" href=\"/\">Home</a></li>\n");
        sb.append("                <li class=\"nav-item\"><a class=\"nav-link\" href=\"/about\">About</a></li>\n");
        sb.append("                <li class=\"nav-item\"><a class=\"nav-link\" href=\"/contact\">Contact</a></li>\n");
        sb.append("            </ul>\n");
        Boolean loggedIn = (Boolean) model.get("isLoggedIn");
        String userEmail = (String) model.get("userEmail");
        if (loggedIn != null && loggedIn && userEmail != null) {
            sb.append("            <span class=\"navbar-text me-3\">").append(HtmlComponents.escapeHtml(userEmail)).append("</span>\n");
            sb.append("            <form class=\"d-flex\" action=\"/logout\" method=\"post\">\n");
            sb.append("                <input type=\"hidden\" name=\"").append(ctx.csrfParameterName()).append("\" value=\"").append(ctx.csrfToken()).append("\">\n");
            sb.append("                <button class=\"btn btn-outline-light\">Logout</button>\n");
            sb.append("            </form>\n");
        } else {
            sb.append("            <a class=\"btn btn-outline-light\" href=\"/login\">Login</a>\n");
            sb.append("            <a class=\"btn btn-primary ms-2\" href=\"/register\">Register</a>\n");
        }
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("</nav>\n");
        return sb.toString();
    }

    private String renderPagination(Integer currentPage, Integer totalPages, String filterTab) {
        if (totalPages == null || totalPages <= 1) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<nav aria-label=\"Page navigation\"><ul class=\"pagination\">\n");
        int cp = currentPage != null ? currentPage : 0;
        String tabParam = filterTab != null && !filterTab.isBlank() ? "&tab=" + filterTab : "";
        sb.append("    <li class=\"page-item ").append(cp <= 0 ? "disabled" : "").append("\">\n");
        sb.append("        <a class=\"page-link\" href=\"/outputs?page=").append(cp - 1).append(tabParam).append("\">Previous</a>\n");
        sb.append("    </li>\n");
        for (int i = 0; i < totalPages; i++) {
            sb.append("    <li class=\"page-item ").append(i == cp ? "active" : "").append("\">\n");
            sb.append("        <a class=\"page-link\" href=\"/outputs?page=").append(i).append(tabParam).append("\">").append(i + 1).append("</a>\n");
            sb.append("    </li>\n");
        }
        sb.append("    <li class=\"page-item ").append(cp >= totalPages - 1 ? "disabled" : "").append("\">\n");
        sb.append("        <a class=\"page-link\" href=\"/outputs?page=").append(cp + 1).append(tabParam).append("\">Next</a>\n");
        sb.append("    </li>\n");
        sb.append("</ul></nav>\n");
        return sb.toString();
    }
}
