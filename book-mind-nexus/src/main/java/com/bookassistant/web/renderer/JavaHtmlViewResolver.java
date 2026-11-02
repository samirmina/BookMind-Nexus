package com.bookassistant.web.renderer;

import com.bookassistant.web.renderer.HtmlBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import java.util.Locale;
import java.util.Map;

/**
 * ViewResolver that maps logical view names to pure Java HTML renderer output.
 * Replaces Thymeleaf entirely—no templates, no front-end resources.
 * <p>
 * Mappings:
 * <ul>
 *   <li>{@code "home"} → {@link HomePageRenderer}</li>
 *   <li>{@code "about"} → {@link AboutPageRenderer}</li>
 *   <li>{@code "contact"} → {@link ContactPageRenderer}</li>
 *   <li>{@code "auth/register"} → {@link RegisterPageRenderer}</li>
 *   <li>{@code "auth/login"} → {@link LoginPageRenderer}</li>
 *   <li>{@code "index"} → {@link AppPageRenderer}</li>
 * </ul>
 */
public class JavaHtmlViewResolver implements ViewResolver {

    @Override
    public View resolveViewName(String viewName, Locale locale) throws Exception {
        switch (viewName) {
            case "home":
                return new JavaHtmlView(HomePageRenderer::render);
            case "about":
                return new JavaHtmlView(AboutPageRenderer::render);
            case "contact":
                return new JavaHtmlView(ContactPageRenderer::render);
            case "auth/register":
                return new JavaHtmlView(RegisterPageRenderer::render);
            case "auth/login":
                return new JavaHtmlView(LoginPageRenderer::render);
            case "index":
                return new JavaHtmlView(AppPageRenderer::render);
            default:
                return null;
        }
    }

    /**
     * Wraps a Java HTML renderer as a Spring MVC View.
     */
    private static class JavaHtmlView implements View {

        private final java.util.function.Function<PageContext, String> renderer;

        private JavaHtmlView(java.util.function.Function<PageContext, String> renderer) {
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

                Object cacheTotalImages = model.get("cacheTotalImages");
                if (cacheTotalImages != null) builder.attr("cacheTotalImages", cacheTotalImages);

                Object error = model.get("error");
                if (error != null) builder.attr("error", error.toString());

                Object success = model.get("success");
                if (success != null) builder.attr("success", success.toString());
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
            String html = renderer.apply(ctx);

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