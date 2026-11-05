package com.bookassistant.web.renderer;

import java.util.List;
import java.util.Map;

/**
 * Interface for rendering a UI component from backend model data.
 * Each feature type (qa, summary, analysis, etc.) has its own implementation
 * that knows how to render its specific data structure into HTML.
 */
public interface ComponentRenderer {

    /**
     * Returns the feature type this renderer handles (e.g., "qa", "summary", "outputs").
     */
    String getFeatureType();

    /**
     * Renders the component HTML from the model attributes map.
     * The ctx provides locale, user context, CSRF tokens, etc.
     * The attributes map contains all data passed from the controller.
     */
    String render(PageContext ctx, Map<String, Object> attributes);
}
