package com.bookassistant.web.renderer;

import java.util.Map;

/**
 * Renders QA (question-answer) results from the AI gateway.
 */
public class QaComponentRenderer implements ComponentRenderer {

    @Override
    public String getFeatureType() {
        return "qa";
    }

    @Override
    public String render(PageContext ctx, Map<String, Object> attributes) {
        String qa = (String) attributes.get("qa");
        if (qa == null || qa.isEmpty()) return "";
        String dir = (String) attributes.get("qaDirection");
        String htmlAttrs = (String) attributes.get("qaHtmlAttributes");
        String inlineStyle = (String) attributes.get("qaInlineStyle");
        return HtmlComponents.card(
            ctx.getMessage("qa.title"),
            "<div " + (dir != null ? "dir=\"" + dir + "\" " : "") +
                (htmlAttrs != null ? htmlAttrs + " " : "") +
                (inlineStyle != null ? "style=\"" + inlineStyle + "\"" : "") + ">" +
                qa + "</div>",
            null
        );
    }
}
