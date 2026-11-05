package com.bookassistant.web.renderer;

import java.util.Map;

/**
 * Renders analysis results (reality check, concept map, risk flags) from the AI gateway.
 */
public class AnalysisComponentRenderer implements ComponentRenderer {

    @Override
    public String getFeatureType() {
        return "analysis";
    }

    @Override
    public String render(PageContext ctx, Map<String, Object> attributes) {
        StringBuilder sb = new StringBuilder();

        String realityCheck = (String) attributes.get("realityCheck");
        if (realityCheck != null && !realityCheck.isEmpty()) {
            sb.append(HtmlComponents.card(
                ctx.getMessage("analysis.realityCheck.title"),
                renderDirDiv(realityCheck, attributes.get("realityCheckDirection"),
                    attributes.get("realityCheckHtmlAttributes"), attributes.get("realityCheckInlineStyle")),
                null
            ));
        }

        String conceptMap = (String) attributes.get("conceptMap");
        if (conceptMap != null && !conceptMap.isEmpty()) {
            sb.append(HtmlComponents.card(
                ctx.getMessage("analysis.conceptMap.title"),
                renderDirDiv(conceptMap, attributes.get("conceptMapDirection"),
                    attributes.get("conceptMapHtmlAttributes"), attributes.get("conceptMapInlineStyle")),
                null
            ));
        }

        String riskFlags = (String) attributes.get("riskFlags");
        if (riskFlags != null && !riskFlags.isEmpty()) {
            sb.append(HtmlComponents.card(
                ctx.getMessage("analysis.riskFlags.title"),
                renderDirDiv(riskFlags, attributes.get("riskFlagsDirection"),
                    attributes.get("riskFlagsHtmlAttributes"), attributes.get("riskFlagsInlineStyle")),
                null
            ));
        }

        return sb.toString();
    }

    private String renderDirDiv(String content, Object dir, Object htmlAttrs, Object inlineStyle) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div ");
        if (dir != null) sb.append("dir=\"").append(dir).append("\" ");
        if (htmlAttrs != null) sb.append(htmlAttrs).append(" ");
        if (inlineStyle != null) sb.append("style=\"").append(inlineStyle).append("\" ");
        sb.append(">").append(content).append("</div>");
        return sb.toString();
    }
}
