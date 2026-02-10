package net.menoita.sitemap.annotation;

import net.menoita.sitemap.model.ChangeFrequency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method or class for inclusion in the generated sitemap.
 *
 * <p>When placed on a <strong>class</strong> (e.g. a {@code @Controller} or {@code @RestController}),
 * all eligible GET endpoints in that class are included in the sitemap.</p>
 *
 * <p>When placed on a <strong>method</strong>, only that specific endpoint is included.</p>
 *
 * <p>All attribute values follow the
 * <a href="https://www.sitemaps.org/protocol.html">sitemaps.org protocol specification</a>.</p>
 *
 * <h3>Locale resolution priority chain</h3>
 * <ol>
 *   <li>{@link #locales()} on this annotation (highest priority)</li>
 *   <li>{@code sitemap.locales} configuration property</li>
 *   <li>Auto-detection from Spring's {@code LocaleResolver} bean (lowest priority)</li>
 * </ol>
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * @Sitemap(priority = 0.8, changefreq = ChangeFrequency.WEEKLY)
 * @GetMapping("/about")
 * public String about() { return "about"; }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sitemap {

    /**
     * Priority of this URL relative to other URLs on the site.
     * Valid values range from 0.0 to 1.0.
     *
     * <p>A value of {@code -1} (the default) means "use the global default priority"
     * from {@code sitemap.default-priority} configuration.</p>
     *
     * @return the priority, or -1 to use the global default
     */
    double priority() default -1;

    /**
     * How frequently the page is likely to change.
     *
     * <p>{@link ChangeFrequency#UNSET} (the default) means "use the global default"
     * from {@code sitemap.default-changefreq} configuration, or omit entirely if
     * no default is configured.</p>
     *
     * @return the change frequency hint
     */
    ChangeFrequency changefreq() default ChangeFrequency.UNSET;

    /**
     * Last modification date in W3C Datetime format string.
     *
     * <p>Supported formats:</p>
     * <ul>
     *   <li>{@code "2025-01-15"} (date only)</li>
     *   <li>{@code "2025-01-15T10:30:00"} (date and time)</li>
     * </ul>
     *
     * <p>This string is parsed to {@code LocalDateTime} at scan time.
     * An empty string (the default) means "not set".</p>
     *
     * <p><em>Note:</em> Java annotations cannot use {@code LocalDateTime} directly,
     * hence the use of a String that is parsed by the endpoint scanner.</p>
     *
     * @return the last modification date string, or empty if not set
     */
    String lastmod() default "";

    /**
     * Locale codes for hreflang alternate link generation.
     *
     * <p>When set (non-empty array), this <strong>overrides</strong> both the
     * configuration-level locales and auto-detected locales for this endpoint.
     * An empty array (the default) means "fall back to config or auto-detection".</p>
     *
     * <p>Example: {@code @Sitemap(locales = {"en", "pt", "es"})}</p>
     *
     * @return array of locale codes, or empty to use config/auto-detection
     * @see net.menoita.sitemap.core.SitemapLocaleResolver
     */
    String[] locales() default {};
}
