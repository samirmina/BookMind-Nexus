package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import java.util.Locale;
import java.util.Map;

/**
 * ViewResolver that maps ALL logical view names to the DynamicPageRenderer.
 * The DynamicPageRenderer assembles pages from component renderers registered
 * in the ComponentRegistry, ensuring every backend feature has a frontend
 * counterpart.
 * <p>
 * No Thymeleaf, no templates, no front-end resources—pure Java rendering.
 */
public class JavaHtmlViewResolver implements ViewResolver {

    private final ComponentRegistry registry;
    private final DynamicPageRenderer dynamicRenderer;

    public JavaHtmlViewResolver() {
        this.registry = new ComponentRegistry();
        // Register all feature component renderers
        registry.register("qa", new QaComponentRenderer());
        registry.register("summary", new SummaryComponentRenderer());
        registry.register("analysis", new AnalysisComponentRenderer());
        registry.register("search", new SearchComponentRenderer());
        registry.register("comprehension", new ComprehensionComponentRenderer());
        registry.register("actionPlan", new ActionPlanComponentRenderer());
        this.dynamicRenderer = new DynamicPageRenderer(registry);
    }

    @Override
    public View resolveViewName(String viewName, Locale locale) throws Exception {
        // All view names are handled by the DynamicPageRenderer
        return new JavaHtmlView((ctx, model) -> dynamicRenderer.render(ctx, model, viewName));
    }

    /**
     * Wraps a Java HTML renderer as a Spring MVC View.
     */
    private static class JavaHtmlView implements View {

        private final java.util.function.BiFunction<PageContext, Map<String, Object>, String> renderer;

        private JavaHtmlView(java.util.function.BiFunction<PageContext, Map<String, Object>, String> renderer) {
            this.renderer = renderer;
        }

        @Override
        public String getContentType() {
            return "text/html;charset=UTF-8";
        }

        @Override
        public void render(Map<String, ?> model, HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
            Locale locale = LocaleContextHolder.getLocale();

            PageContext.Builder builder = PageContext.builder()
                .locale(locale)
                .lang(locale.getLanguage())
                .dir(isArabic(locale) ? "rtl" : "ltr");

            // Extract model attributes into PageContext
            if (model != null) {
                Object userId = model.get("userId");
                if (userId != null) builder.userId(Long.valueOf(userId.toString()));

                Object userEmail = model.get("userEmail");
                if (userEmail != null) builder.userEmail(userEmail.toString());

                Object isLoggedIn = model.get("isLoggedIn");
                if (isLoggedIn != null) builder.isLoggedIn(Boolean.parseBoolean(isLoggedIn.toString()));

                Object hasBook = model.get("hasBook");
                if (hasBook != null) builder.attr("hasBook", hasBook);

                Object bookLanguage = model.get("bookLanguage");
                if (bookLanguage != null) builder.bookLanguage(bookLanguage.toString());

                Object bookName = model.get("bookName");
                if (bookName != null) builder.attr("bookName", bookName.toString());

                Object cacheBookName = model.get("cacheBookName");
                if (cacheBookName != null) builder.attr("cacheBookName", cacheBookName.toString());

                Object error = model.get("error");
                if (error != null) builder.attr("error", error.toString());

                Object success = model.get("success");
                if (success != null) builder.attr("success", success.toString());
            }

            // Extract CSRF token from request for form protection
            CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
            if (csrf != null) {
                builder.csrfParameterName(csrf.getParameterName());
                builder.csrfToken(csrf.getToken());
            }

            // Also check session for user data
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessUserId = session.getAttribute("userId");
                if (sessUserId != null && model == null) {
                    builder.userId(Long.valueOf(sessUserId.toString()));
                }
                Object sessUserEmail = session.getAttribute("userEmail");
                if (sessUserEmail != null && model == null) {
                    builder.userEmail(sessUserEmail.toString());
                }
            }

            PageContext ctx = builder.build();

            // Convert model to Map<String, Object> for the renderer
            Map<String, Object> modelMap = new java.util.HashMap<>();
            if (model != null) {
                for (Map.Entry<String, ?> entry : model.entrySet()) {
                    modelMap.put(entry.getKey(), entry.getValue());
                }
            }

            String html = renderer.apply(ctx, modelMap);

            response.setContentType(getContentType());
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(html);
        }

        private boolean isArabic(Locale locale) {
            String lang = locale.getLanguage();
            return "ar".equals(lang);
        }
    }
}
