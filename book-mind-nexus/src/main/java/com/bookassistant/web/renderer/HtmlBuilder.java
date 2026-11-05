package com.bookassistant.web.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class HtmlBuilder {
    private final StringBuilder sb = new StringBuilder();
    private int indentLevel = 0;
    private static final String INDENT = "  ";

    private HtmlBuilder() {}

    public static HtmlBuilder create() {
        return new HtmlBuilder();
    }

    public HtmlBuilder doctype() {
        sb.append("<!DOCTYPE html>");
        return this;
    }

    public HtmlBuilder html(Consumer<HtmlBuilder> content) {
        return tag("html", content);
    }

    public HtmlBuilder html(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("html", content, attrs);
    }

    public HtmlBuilder head(Consumer<HtmlBuilder> content) {
        return tag("head", content);
    }

    public HtmlBuilder body(Consumer<HtmlBuilder> content) {
        return tag("body", content);
    }

    public HtmlBuilder body(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("body", content, attrs);
    }

    public HtmlBuilder tag(String name, Consumer<HtmlBuilder> content) {
        return tag(name, content, new String[0]);
    }

    public HtmlBuilder tag(String name, Consumer<HtmlBuilder> content, String... attrs) {
        indent();
        sb.append("<").append(name);
        for (int i = 0; i + 1 < attrs.length; i += 2) {
            sb.append(" ").append(attrs[i]).append("=\"").append(escape(attrs[i + 1])).append("\"");
        }
        sb.append(">");
        int savedTagEnd = lastTagEnd;
        lastTagEnd = sb.length() - 1;
        indentLevel++;
        content.accept(this);
        lastTagEnd = savedTagEnd;
        indentLevel--;
        indent();
        sb.append("</").append(name).append(">");
        return this;
    }

    public HtmlBuilder voidTag(String name, String... attrs) {
        indent();
        sb.append("<").append(name);
        for (int i = 0; i + 1 < attrs.length; i += 2) {
            sb.append(" ").append(attrs[i]).append("=\"").append(escape(attrs[i + 1])).append("\"");
        }
        sb.append(" />");
        return this;
    }

    public HtmlBuilder text(String text) {
        indent();
        sb.append(escape(text));
        return this;
    }

    public HtmlBuilder raw(String html) {
        indent();
        sb.append(html);
        return this;
    }

    public HtmlBuilder comment(String text) {
        indent();
        sb.append("<!-- ").append(text).append(" -->");
        return this;
    }

    public HtmlBuilder script(Consumer<HtmlBuilder> content) {
        return tag("script", content);
    }

    public HtmlBuilder script(String src) {
        return voidTag("script", "src", src);
    }

    public HtmlBuilder style(Consumer<HtmlBuilder> content) {
        return tag("style", content);
    }

    public HtmlBuilder link(String href, String rel) {
        return voidTag("link", "href", href, "rel", rel);
    }

    public HtmlBuilder meta(String... attrs) {
        return voidTag("meta", attrs);
    }

    public HtmlBuilder title(String text) {
        return tag("title", b -> b.text(text));
    }

    public HtmlBuilder div(String text, String... attrs) {
        return div(b -> b.text(text), attrs);
    }

    public HtmlBuilder span(String text, String... attrs) {
        return span(b -> b.text(text), attrs);
    }

    public HtmlBuilder nav(String text, String... attrs) {
        return nav(b -> b.text(text), attrs);
    }

    public HtmlBuilder header(String text, String... attrs) {
        return header(b -> b.text(text), attrs);
    }

    public HtmlBuilder footer(String text, String... attrs) {
        return footer(b -> b.text(text), attrs);
    }

    public HtmlBuilder main(String text, String... attrs) {
        return main(b -> b.text(text), attrs);
    }

    public HtmlBuilder section(String text, String... attrs) {
        return section(b -> b.text(text), attrs);
    }

    public HtmlBuilder article(String text, String... attrs) {
        return article(b -> b.text(text), attrs);
    }

    public HtmlBuilder ul(String text, String... attrs) {
        return ul(b -> b.text(text), attrs);
    }

    public HtmlBuilder ol(String text, String... attrs) {
        return ol(b -> b.text(text), attrs);
    }

    public HtmlBuilder li(String text, String... attrs) {
        return li(b -> b.text(text), attrs);
    }

    public HtmlBuilder table(String text, String... attrs) {
        return table(b -> b.text(text), attrs);
    }

    public HtmlBuilder thead(String text) {
        return thead(b -> b.text(text));
    }

    public HtmlBuilder tbody(String text) {
        return tbody(b -> b.text(text));
    }

    public HtmlBuilder tr(String text, String... attrs) {
        return tr(b -> b.text(text), attrs);
    }

    public HtmlBuilder textarea(String text, String... attrs) {
        return textarea(b -> b.text(text), attrs);
    }

    public HtmlBuilder select(String text, String... attrs) {
        return select(b -> b.text(text), attrs);
    }

    public HtmlBuilder button(String text, String... attrs) {
        return button(b -> b.text(text), attrs);
    }

    public HtmlBuilder div(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("div", content, attrs);
    }

    public HtmlBuilder span(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("span", content, attrs);
    }

    public HtmlBuilder a(String href, Consumer<HtmlBuilder> content) {
        return tag("a", content, "href", href);
    }

    public HtmlBuilder button(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("button", content, attrs);
    }

    public HtmlBuilder form(String action, String method, Consumer<HtmlBuilder> content, String... attrs) {
        List<String> attrList = new ArrayList<>();
        attrList.add("action");
        attrList.add(action);
        attrList.add("method");
        attrList.add(method);
        for (int i = 0; i < attrs.length; i++) {
            attrList.add(attrs[i]);
        }
        return tag("form", content, attrList.toArray(new String[0]));
    }

    public HtmlBuilder input(String type, String name, String... attrs) {
        List<String> attrList = new ArrayList<>();
        attrList.add("type");
        attrList.add(type);
        attrList.add("name");
        attrList.add(name);
        for (int i = 0; i < attrs.length; i++) {
            attrList.add(attrs[i]);
        }
        return voidTag("input", attrList.toArray(new String[0]));
    }

    public HtmlBuilder select(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("select", content, attrs);
    }

    public HtmlBuilder option(String value, String text, boolean selected) {
        String[] attrs = selected ? new String[]{"value", value, "selected", "selected"} : new String[]{"value", value};
        return tag("option", b -> b.text(text), attrs);
    }

    public HtmlBuilder h1(String text, String... attrs) {
        return tag("h1", b -> b.text(text), attrs);
    }

    public HtmlBuilder h2(String text, String... attrs) {
        return tag("h2", b -> b.text(text), attrs);
    }

    public HtmlBuilder h3(String text, String... attrs) {
        return tag("h3", b -> b.text(text), attrs);
    }

    public HtmlBuilder h4(String text, String... attrs) {
        return tag("h4", b -> b.text(text), attrs);
    }

    public HtmlBuilder h5(String text, String... attrs) {
        return tag("h5", b -> b.text(text), attrs);
    }

    public HtmlBuilder p(String text, String... attrs) {
        return tag("p", b -> b.text(text), attrs);
    }

    public HtmlBuilder p(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("p", content, attrs);
    }

    public HtmlBuilder ul(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("ul", content, attrs);
    }

    public HtmlBuilder ol(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("ol", content, attrs);
    }

    public HtmlBuilder li(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("li", content, attrs);
    }

    public HtmlBuilder label(String text, String... attrs) {
        return tag("label", b -> b.text(text), attrs);
    }

    public HtmlBuilder nav(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("nav", content, attrs);
    }

    public HtmlBuilder header(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("header", content, attrs);
    }

    public HtmlBuilder footer(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("footer", content, attrs);
    }

    public HtmlBuilder main(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("main", content, attrs);
    }

    public HtmlBuilder section(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("section", content, attrs);
    }

    public HtmlBuilder article(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("article", content, attrs);
    }

    public HtmlBuilder img(String src, String alt, String... attrs) {
        List<String> attrList = new ArrayList<>();
        attrList.add("src");
        attrList.add(src);
        attrList.add("alt");
        attrList.add(alt);
        for (int i = 0; i < attrs.length; i++) {
            attrList.add(attrs[i]);
        }
        return voidTag("img", attrList.toArray(new String[0]));
    }

    public HtmlBuilder table(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("table", content, attrs);
    }

    public HtmlBuilder thead(Consumer<HtmlBuilder> content) {
        return tag("thead", content);
    }

    public HtmlBuilder tbody(Consumer<HtmlBuilder> content) {
        return tag("tbody", content);
    }

    public HtmlBuilder tr(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("tr", content, attrs);
    }

    public HtmlBuilder th(String text, String... attrs) {
        return tag("th", b -> b.text(text), attrs);
    }

    public HtmlBuilder td(String text, String... attrs) {
        return tag("td", b -> b.text(text), attrs);
    }

    public HtmlBuilder tr(Consumer<HtmlBuilder> content) {
        return tag("tr", content);
    }

    public HtmlBuilder th(Consumer<HtmlBuilder> content) {
        return tag("th", content);
    }

    public HtmlBuilder td(Consumer<HtmlBuilder> content) {
        return tag("td", content);
    }

    public HtmlBuilder textarea(Consumer<HtmlBuilder> content, String... attrs) {
        return tag("textarea", content, attrs);
    }

private int lastTagEnd = -1;

    public HtmlBuilder attr(String name, String value) {
        if (lastTagEnd >= 0) {
            String attrStr = " " + name + "=\"" + escape(value) + "\"";
            sb.insert(lastTagEnd, attrStr);
            lastTagEnd += attrStr.length();
        }
        return this;
    }

    private void indent() {
        sb.append("\n");
        for (int i = 0; i < indentLevel; i++) {
            sb.append(INDENT);
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public String build() {
        return sb.toString();
    }
}