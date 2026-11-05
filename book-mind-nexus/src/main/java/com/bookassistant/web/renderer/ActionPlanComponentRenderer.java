package com.bookassistant.web.renderer;

import java.util.Map;

/**
 * Renders action plan results from the AI gateway.
 */
public class ActionPlanComponentRenderer implements ComponentRenderer {

    @Override
    public String getFeatureType() {
        return "actionPlan";
    }

    @Override
    public String render(PageContext ctx, Map<String, Object> attributes) {
        String actionPlan = (String) attributes.get("actionPlan");
        if (actionPlan == null || actionPlan.isEmpty()) return "";
        return HtmlComponents.card(
            ctx.getMessage("plan.title"),
            actionPlan,
            null
        );
    }
}
