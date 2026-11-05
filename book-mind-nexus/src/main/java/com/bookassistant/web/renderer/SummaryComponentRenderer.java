package com.bookassistant.web.renderer;

import java.util.Map;

/**
 * Renders summary results (general and page-level) from the AI gateway.
 */
public class SummaryComponentRenderer implements ComponentRenderer {

    @Override
    public String getFeatureType() {
        return "summary";
    }

    @Override
    public String render(PageContext ctx, Map<String, Object> attributes) {
        StringBuilder sb = new StringBuilder();

        String summaryGeneral = (String) attributes.get("summaryGeneral");
        if (summaryGeneral != null && !summaryGeneral.isEmpty()) {
            sb.append(HtmlComponents.card(
                ctx.getMessage("summary.title.general"),
                "<div " + (attributes.get("summaryGeneralDirection") != null ?
                    "dir=\"" + attributes.get("summaryGeneralDirection") + "\" " : "") + ">" +
                summaryGeneral + "</div>",
                null
            ));
        }

        String summaryPages = (String) attributes.get("summaryPages");
        if (summaryPages != null && !summaryPages.isEmpty()) {
            sb.append(HtmlComponents.card(
                ctx.getMessage("summary.title.pages"),
                "<div " + (attributes.get("summaryPagesDirection") != null ?
                    "dir=\"" + attributes.get("summaryPagesDirection") + "\" " : "") + ">" +
                summaryPages + "</div>",
                null
            ));
        }

        return sb.toString();
    }
}
