package net.menoita.sitemap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a controller method from sitemap generation.
 *
 * <p>This annotation takes effect even when auto-scan is enabled or a class-level
 * {@link Sitemap @Sitemap} annotation is present. It provides an opt-out mechanism
 * for specific endpoints that should not appear in the sitemap.</p>
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * @Sitemap  // class-level: all endpoints included by default
 * @RestController
 * public class PageController {
 *
 *     @GetMapping("/public")
 *     public String publicPage() { ... }  // included
 *
 *     @SitemapExclude
 *     @GetMapping("/admin")
 *     public String adminPage() { ... }   // excluded
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SitemapExclude {
}
