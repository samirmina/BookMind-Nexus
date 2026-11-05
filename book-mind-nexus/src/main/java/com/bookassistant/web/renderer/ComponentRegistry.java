package com.bookassistant.web.renderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping feature type names to their component renderers.
 * Controllers set model attributes with feature type keys, and the
 * DynamicPageRenderer looks up the appropriate renderer for each.
 */
public class ComponentRegistry {

    private final Map<String, ComponentRenderer> renderers = new HashMap<>();

    public void register(String featureType, ComponentRenderer renderer) {
        renderers.put(featureType, renderer);
    }

    public ComponentRenderer get(String featureType) {
        return renderers.get(featureType);
    }

    public Map<String, ComponentRenderer> getAll() {
        return renderers;
    }

    public boolean has(String featureType) {
        return renderers.containsKey(featureType);
    }
}
