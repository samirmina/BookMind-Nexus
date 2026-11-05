package com.bookassistant.web.renderer;

import java.util.Map;

/**
 * Renders semantic search results from the AI gateway.
 */
public class SearchComponentRenderer implements ComponentRenderer {

    @Override
    public String getFeatureType() {
        return "search";
    }

    @Override
    public String render(PageContext ctx, Map<String, Object> attributes) {
        String semanticSearch = (String) attributes.get("semanticSearch");
        if (semanticSearch == null || semanticSearch.isEmpty()) return "";
        return HtmlComponents.card(
            ctx.getMessage("search.title"),
            semanticSearch,
            null
        );
    }
}
