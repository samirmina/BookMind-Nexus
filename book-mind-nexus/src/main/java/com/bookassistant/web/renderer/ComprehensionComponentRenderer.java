package com.bookassistant.web.renderer;

import java.util.Map;

/**
 * Renders comprehension results from the AI gateway.
 */
public class ComprehensionComponentRenderer implements ComponentRenderer {

    @Override
    public String getFeatureType() {
        return "comprehension";
    }

    @Override
    public String render(PageContext ctx, Map<String, Object> attributes) {
        String comprehension = (String) attributes.get("comprehension");
        if (comprehension == null || comprehension.isEmpty()) return "";
        String dir = (String) attributes.get("comprehensionDirection");
        return HtmlComponents.card(
            ctx.getMessage("comprehension.title"),
            "<div " + (dir != null ? "dir=\"" + dir + "\" " : "") + ">" +
            comprehension + "</div>",
            null
        );
    }
}
