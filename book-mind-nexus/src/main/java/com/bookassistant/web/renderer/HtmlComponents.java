package com.bookassistant.web.renderer;

import java.util.List;
import java.util.Map;

/**
 * Utility for generating common HTML UI components (cards, alerts, tables,
 * buttons, forms, navbars, breadcrumbs) used by feature renderers.
 */
public class HtmlComponents {

    private HtmlComponents() {}

    /**
     * Wraps content in a Bootstrap card with optional header and footer.
     */
    public static String card(String header, String body, String footer) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card mb-3\">");
        if (header != null && !header.isEmpty()) {
            sb.append("<div class=\"card-header\">").append(escapeHtml(header)).append("</div>");
        }
        sb.append("<div class=\"card-body\">").append(body).append("</div>");
        if (footer != null && !footer.isEmpty()) {
            sb.append("<div class=\"card-footer text-muted\">").append(footer).append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Renders an alert box (success, danger, warning, info).
     */
    public static String alert(String type, String message, boolean dismissible) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"alert alert-").append(type).append(" alert-dismissible fade show\" role=\"alert\">");
        sb.append(escapeHtml(message));
        if (dismissible) {
            sb.append("<button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\"></button>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Renders a data table from a list of row maps.
     */
    public static String table(List<String> headers, List<List<String>> rows, String rowActions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"table-responsive\"><table class=\"table table-striped table-hover\">");
        sb.append("<thead><tr>");
        for (String h : headers) {
            sb.append("<th>").append(escapeHtml(h)).append("</th>");
        }
        if (rowActions != null) {
            sb.append("<th>").append(rowActions).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            sb.append("<tr>");
            for (String cell : row) {
                sb.append("<td>").append(escapeHtml(cell)).append("</td>");
            }
            if (rowActions != null) {
                sb.append("<td>").append(rowActions).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    /**
     * Renders a button.
     */
    public static String button(String label, String type, String action, String extraClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<button type=\"").append(type).append("\" class=\"btn btn-").append(type);
        if (extraClass != null) {
            sb.append(" ").append(extraClass);
        }
        sb.append("\"");
        if (action != null && !action.isEmpty()) {
            sb.append(" ").append(action);
        }
        sb.append(">").append(escapeHtml(label)).append("</button>");
        return sb.toString();
    }

    /**
     * Renders a form with CSRF token and given fields.
     */
    public static String formStart(String action, String method, String csrfName, String csrfToken) {
        return "<form action=\"" + escapeHtml(action) + "\" method=\"" + method + "\">"
             + "<input type=\"hidden\" name=\"" + escapeHtml(csrfName) + "\" value=\"" + escapeHtml(csrfToken) + "\">";
    }

    public static String formEnd() {
        return "</form>";
    }

    /**
     * Renders a form input field.
     */
    public static String input(String type, String name, String label, String value, boolean required) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"mb-3\">");
        if (label != null) {
            sb.append("<label class=\"form-label\">").append(escapeHtml(label)).append("</label>");
        }
        sb.append("<input type=\"").append(type).append("\" name=\"").append(escapeHtml(name))
          .append("\" class=\"form-control\"");
        if (value != null) {
            sb.append(" value=\"").append(escapeHtml(value)).append("\"");
        }
        if (required) {
            sb.append(" required");
        }
        sb.append("></div>");
        return sb.toString();
    }

    /**
     * Renders a navigation bar with brand and links.
     */
    public static String navbar(String brandName, String brandUrl, List<Map<String, String>> links, String userEmail, boolean loggedIn) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"navbar navbar-expand-lg navbar-dark bg-dark mb-3\">");
        sb.append("<div class=\"container-fluid\">");
        sb.append("<a class=\"navbar-brand\" href=\"").append(escapeHtml(brandUrl)).append("\">")
          .append(escapeHtml(brandName)).append("</a>");
        sb.append("<div class=\"collapse navbar-collapse\">");
        sb.append("<ul class=\"navbar-nav me-auto mb-2 mb-lg-0\">");
        if (links != null) {
            for (Map<String, String> link : links) {
                sb.append("<li class=\"nav-item\"><a class=\"nav-link\" href=\"")
                  .append(escapeHtml(link.get("url"))).append("\">")
                  .append(escapeHtml(link.get("label"))).append("</a></li>");
            }
        }
        sb.append("</ul>");
        if (loggedIn && userEmail != null) {
            sb.append("<span class=\"navbar-text me-3\">").append(escapeHtml(userEmail)).append("</span>");
            sb.append("<form class=\"d-flex\" action=\"/logout\" method=\"post\">");
            sb.append("<button class=\"btn btn-outline-light\">Logout</button>");
            sb.append("</form>");
        } else {
            sb.append("<a class=\"btn btn-outline-light\" href=\"/login\">Login</a>");
            sb.append("<a class=\"btn btn-primary ms-2\" href=\"/register\">Register</a>");
        }
        sb.append("</div></div></nav>");
        return sb.toString();
    }

    /**
     * Renders a breadcrumb trail.
     */
    public static String breadcrumb(List<Map<String, String>> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<nav aria-label=\"breadcrumb\"><ol class=\"breadcrumb\">");
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            boolean last = i == items.size() - 1;
            if (last) {
                sb.append("<li class=\"breadcrumb-item active\">").append(escapeHtml(item.get("label"))).append("</li>");
            } else {
                sb.append("<li class=\"breadcrumb-item\"><a href=\"")
                  .append(escapeHtml(item.get("url"))).append("\">")
                  .append(escapeHtml(item.get("label"))).append("</a></li>");
            }
        }
        sb.append("</ol></nav>");
        return sb.toString();
    }

    /**
     * Renders a section heading.
     */
    public static String section(String title) {
        return "<h2 class=\"section-title mb-4\">" + escapeHtml(title) + "</h2>";
    }

    /**
     * Escapes HTML special characters.
     */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
