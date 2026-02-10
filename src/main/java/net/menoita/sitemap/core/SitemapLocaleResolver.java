package net.menoita.sitemap.core;

import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.config.SitemapProperties.LocaleUrlPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves locales for sitemap hreflang generation using a three-tier priority chain.
 *
 * <p>The priority chain (highest to lowest):</p>
 * <ol>
 *   <li>{@code @Sitemap(locales={...})} annotation on the method/class</li>
 *   <li>{@code sitemap.locales} configuration property</li>
 *   <li>Auto-detection from Spring's {@link LocaleResolver} bean</li>
 * </ol>
 *
 * <p>If no locales are resolved at any level, no hreflang alternates are generated
 * and the URL is added without any locale prefix or query parameter.</p>
 *
 * <h3>URL construction</h3>
 * <p>Supports two patterns configured via {@code sitemap.locale-url-pattern}:</p>
 * <ul>
 *   <li>{@link LocaleUrlPattern#PATH_PREFIX}: {@code baseUrl + "/" + locale + path}
 *       (e.g. {@code https://example.com/en/about})</li>
 *   <li>{@link LocaleUrlPattern#QUERY_PARAM}: {@code baseUrl + path + "?param=" + locale}
 *       (e.g. {@code https://dommy.io/?lang=fr})</li>
 * </ul>
 */
public class SitemapLocaleResolver {

    private static final Logger log = LoggerFactory.getLogger(SitemapLocaleResolver.class);

    private final SitemapProperties properties;
    private final LocaleResolver localeResolver;

    /**
     * Constructs a new SitemapLocaleResolver.
     *
     * @param properties     the sitemap configuration properties
     * @param localeResolver the Spring LocaleResolver for auto-detection (may be null)
     */
    public SitemapLocaleResolver(SitemapProperties properties, LocaleResolver localeResolver) {
        this.properties = properties;
        this.localeResolver = localeResolver;
    }

    /**
     * Resolves locales for an endpoint, applying the three-tier priority chain.
     *
     * <ol>
     *   <li>If {@code annotationLocales} is non-empty, those are used (highest priority).</li>
     *   <li>Otherwise, if {@code sitemap.locales} config property is non-empty, those are used.</li>
     *   <li>Otherwise, attempts auto-detection from Spring's {@link LocaleResolver}.
     *       Since {@code LocaleResolver} typically resolves a single locale per-request,
     *       auto-detection returns the default locale only. For multi-locale support,
     *       explicit configuration is recommended.</li>
     * </ol>
     *
     * @param annotationLocales locales from the {@code @Sitemap} annotation (may be empty or null)
     * @return resolved list of locale codes, or empty if none configured at any level
     */
    public List<String> resolveLocales(String[] annotationLocales) {
        // Priority 1: annotation-level locales
        return Optional.ofNullable(annotationLocales)
                .filter(arr -> arr.length > 0)
                .map(arr -> {
                    log.debug("Using annotation-level locales: {}", Arrays.toString(arr));
                    return Arrays.asList(arr);
                })
                // Priority 2: config-level locales
                .or(() -> Optional.ofNullable(properties.getLocales())
                        .filter(list -> !list.isEmpty())
                        .map(list -> {
                            log.debug("Using config-level locales: {}", list);
                            return list;
                        }))
                // Priority 3: auto-detect from Spring's LocaleResolver.
                // Note: LocaleResolver.resolveLocale() requires an HttpServletRequest, which is
                // unavailable at scan time. Spring Boot also always registers a default
                // AcceptHeaderLocaleResolver, making Locale.getDefault() unreliable as a signal.
                // For multilingual sitemaps, explicitly configure sitemap.locales or use
                // @Sitemap(locales={...}) on each endpoint.
                .orElseGet(() -> {
                    log.debug("No locales resolved — configure sitemap.locales for multilingual support");
                    return Collections.emptyList();
                });
    }

    /**
     * Builds the full URL for a given path and locale using the configured pattern.
     *
     * <p>Respects {@code omitDefaultLocaleInUrl}: if {@code true} and the locale equals
     * the configured {@code defaultLocale}, the locale identifier is omitted from the URL.</p>
     *
     * <h3>URL construction by pattern</h3>
     * <ul>
     *   <li>{@code PATH_PREFIX}: {@code baseUrl + "/" + locale + path}
     *       → {@code "https://example.com/en/about"}</li>
     *   <li>{@code QUERY_PARAM}: {@code baseUrl + path + "?param=" + locale}
     *       → {@code "https://dommy.io/?lang=fr"}
     *       (uses {@code "&param="} if path already contains query parameters)</li>
     * </ul>
     *
     * @param path   the endpoint path (e.g. {@code "/about"})
     * @param locale the locale code (e.g. {@code "en"})
     * @return the fully qualified URL with locale
     */
    public String buildLocalizedUrl(String path, String locale) {
        var baseUrl = baseUrl();
        var normalPath = ensureLeadingSlash(path);

        // Omit locale identifier for the default locale when configured
        if (properties.isOmitDefaultLocaleInUrl() && locale != null
                && locale.equals(properties.getDefaultLocale())) {
            return baseUrl + normalPath;
        }

        return switch (properties.getLocaleUrlPattern()) {
            case PATH_PREFIX -> baseUrl + "/" + locale + normalPath;
            case QUERY_PARAM -> {
                var fullUrl = baseUrl + normalPath;
                var separator = fullUrl.contains("?") ? "&" : "?";
                yield fullUrl + separator + properties.getLocaleQueryParamName() + "=" + locale;
            }
        };
    }

    /**
     * Builds the full URL for a given path without any locale (non-localized).
     *
     * @param path the endpoint path (e.g. {@code "/about"})
     * @return the fully qualified URL: {@code baseUrl + path}
     */
    public String buildUrl(String path) {
        return baseUrl() + ensureLeadingSlash(path);
    }

    /**
     * Builds the alternates map (hreflang to URL) for all resolved locales of a given path.
     *
     * <p>The map includes an {@code "x-default"} entry pointing to the default locale
     * (or the first locale in the list if no default is configured).</p>
     *
     * @param path    the endpoint path (e.g. {@code "/about"})
     * @param locales the resolved list of locale codes
     * @return an ordered map of hreflang code to URL, including x-default
     */
    public Map<String, String> buildAlternates(String path, List<String> locales) {
        if (locales == null || locales.isEmpty()) {
            return Collections.emptyMap();
        }

        // Build locale -> URL entries preserving order
        var alternates = locales.stream()
                .collect(Collectors.toMap(
                        locale -> locale,
                        locale -> buildLocalizedUrl(path, locale),
                        (a, b) -> a,
                        LinkedHashMap::new));

        // x-default points to the configured default locale, or the first locale
        var xDefaultLocale = Optional.ofNullable(properties.getDefaultLocale())
                .filter(locales::contains)
                .orElse(locales.get(0));
        alternates.put("x-default", buildLocalizedUrl(path, xDefaultLocale));

        return Collections.unmodifiableMap(alternates);
    }

    /** Returns the base URL with trailing slash stripped. */
    private String baseUrl() {
        return SitemapXmlGenerator.stripTrailingSlash(properties.getBaseUrl());
    }

    /** Ensures a path starts with {@code /}. Null/empty becomes {@code /}. */
    private static String ensureLeadingSlash(String path) {
        if (path == null || path.isEmpty()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }
}
