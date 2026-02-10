package net.menoita.sitemap.core;

import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.config.SitemapProperties.LocaleUrlPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SitemapLocaleResolver}.
 * Tests the locale priority chain (annotation > config > LocaleResolver),
 * URL building with both PATH_PREFIX and QUERY_PARAM patterns,
 * and alternates map construction.
 */
class SitemapLocaleResolverTest {

    private SitemapProperties properties;
    private SitemapLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SitemapProperties();
        properties.setBaseUrl("https://example.com");
        properties.setLocaleUrlPattern(LocaleUrlPattern.PATH_PREFIX);
        // No Spring LocaleResolver in unit tests
        resolver = new SitemapLocaleResolver(properties, null);
    }

    // --- Locale resolution priority chain ---

    @Test
    @DisplayName("resolveLocales: annotation locales take highest priority")
    void annotationLocalesTakePriority() {
        properties.setLocales(List.of("en", "pt"));

        List<String> result = resolver.resolveLocales(new String[]{"fr", "de"});

        assertEquals(List.of("fr", "de"), result);
    }

    @Test
    @DisplayName("resolveLocales: config locales used when annotation is empty")
    void configLocalesWhenAnnotationEmpty() {
        properties.setLocales(List.of("en", "pt"));

        List<String> result = resolver.resolveLocales(new String[]{});

        assertEquals(List.of("en", "pt"), result);
    }

    @Test
    @DisplayName("resolveLocales: config locales used when annotation is null")
    void configLocalesWhenAnnotationNull() {
        properties.setLocales(List.of("en", "pt"));

        List<String> result = resolver.resolveLocales(null);

        assertEquals(List.of("en", "pt"), result);
    }

    @Test
    @DisplayName("resolveLocales: returns empty when nothing configured")
    void returnsEmptyWhenNothingConfigured() {
        properties.setLocales(List.of());

        List<String> result = resolver.resolveLocales(new String[]{});

        assertTrue(result.isEmpty());
    }

    // --- PATH_PREFIX URL building ---

    @Test
    @DisplayName("buildLocalizedUrl: PATH_PREFIX pattern builds correct URL")
    void pathPrefixBuildsCorrectUrl() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.PATH_PREFIX);

        String url = resolver.buildLocalizedUrl("/about", "en");

        assertEquals("https://example.com/en/about", url);
    }

    @Test
    @DisplayName("buildLocalizedUrl: PATH_PREFIX with root path")
    void pathPrefixWithRootPath() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.PATH_PREFIX);

        String url = resolver.buildLocalizedUrl("/", "fr");

        assertEquals("https://example.com/fr/", url);
    }

    // --- QUERY_PARAM URL building ---

    @Test
    @DisplayName("buildLocalizedUrl: QUERY_PARAM pattern builds correct URL")
    void queryParamBuildsCorrectUrl() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.QUERY_PARAM);
        properties.setLocaleQueryParamName("lang");

        String url = resolver.buildLocalizedUrl("/about", "fr");

        assertEquals("https://example.com/about?lang=fr", url);
    }

    @Test
    @DisplayName("buildLocalizedUrl: QUERY_PARAM appends with & when existing query params")
    void queryParamAppendsWithAmpersand() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.QUERY_PARAM);
        properties.setLocaleQueryParamName("lang");

        String url = resolver.buildLocalizedUrl("/page?sort=date", "pt");

        assertEquals("https://example.com/page?sort=date&lang=pt", url);
    }

    @Test
    @DisplayName("buildLocalizedUrl: QUERY_PARAM with custom param name")
    void queryParamCustomParamName() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.QUERY_PARAM);
        properties.setLocaleQueryParamName("locale");

        String url = resolver.buildLocalizedUrl("/about", "es");

        assertEquals("https://example.com/about?locale=es", url);
    }

    @Test
    @DisplayName("buildLocalizedUrl: QUERY_PARAM with root path")
    void queryParamWithRootPath() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.QUERY_PARAM);
        properties.setLocaleQueryParamName("lang");

        String url = resolver.buildLocalizedUrl("/", "fr");

        assertEquals("https://example.com/?lang=fr", url);
    }

    // --- Default locale omission ---

    @Test
    @DisplayName("buildLocalizedUrl: omits locale for default when configured")
    void omitsLocaleForDefault() {
        properties.setDefaultLocale("en");
        properties.setOmitDefaultLocaleInUrl(true);
        properties.setLocaleUrlPattern(LocaleUrlPattern.PATH_PREFIX);

        String urlDefault = resolver.buildLocalizedUrl("/about", "en");
        String urlOther = resolver.buildLocalizedUrl("/about", "pt");

        assertEquals("https://example.com/about", urlDefault);
        assertEquals("https://example.com/pt/about", urlOther);
    }

    @Test
    @DisplayName("buildLocalizedUrl: does not omit locale when omitDefaultLocaleInUrl=false")
    void doesNotOmitWhenConfiguredFalse() {
        properties.setDefaultLocale("en");
        properties.setOmitDefaultLocaleInUrl(false);
        properties.setLocaleUrlPattern(LocaleUrlPattern.PATH_PREFIX);

        String url = resolver.buildLocalizedUrl("/about", "en");

        assertEquals("https://example.com/en/about", url);
    }

    @Test
    @DisplayName("buildLocalizedUrl: QUERY_PARAM omits locale for default when configured")
    void queryParamOmitsLocaleForDefault() {
        properties.setDefaultLocale("en");
        properties.setOmitDefaultLocaleInUrl(true);
        properties.setLocaleUrlPattern(LocaleUrlPattern.QUERY_PARAM);
        properties.setLocaleQueryParamName("lang");

        String urlDefault = resolver.buildLocalizedUrl("/about", "en");
        String urlOther = resolver.buildLocalizedUrl("/about", "fr");

        assertEquals("https://example.com/about", urlDefault);
        assertEquals("https://example.com/about?lang=fr", urlOther);
    }

    // --- buildUrl (non-localized) ---

    @Test
    @DisplayName("buildUrl: constructs simple baseUrl + path")
    void buildUrlSimple() {
        assertEquals("https://example.com/about", resolver.buildUrl("/about"));
    }

    @Test
    @DisplayName("buildUrl: normalizes path without leading slash")
    void buildUrlNormalizesPath() {
        assertEquals("https://example.com/about", resolver.buildUrl("about"));
    }

    @Test
    @DisplayName("buildUrl: normalizes base URL with trailing slash")
    void buildUrlNormalizesBaseUrl() {
        properties.setBaseUrl("https://example.com/");
        SitemapLocaleResolver r = new SitemapLocaleResolver(properties, null);

        assertEquals("https://example.com/about", r.buildUrl("/about"));
    }

    // --- buildAlternates ---

    @Test
    @DisplayName("buildAlternates: builds correct alternates map with x-default")
    void buildAlternatesIncludesXDefault() {
        properties.setLocaleUrlPattern(LocaleUrlPattern.PATH_PREFIX);
        properties.setDefaultLocale("en");

        Map<String, String> alternates = resolver.buildAlternates("/about", List.of("en", "pt"));

        assertEquals(3, alternates.size());
        assertEquals("https://example.com/en/about", alternates.get("en"));
        assertEquals("https://example.com/pt/about", alternates.get("pt"));
        assertEquals("https://example.com/en/about", alternates.get("x-default"));
    }

    @Test
    @DisplayName("buildAlternates: x-default uses first locale when no default configured")
    void buildAlternatesXDefaultUsesFirst() {
        properties.setDefaultLocale(null);

        Map<String, String> alternates = resolver.buildAlternates("/about", List.of("pt", "en"));

        assertEquals("https://example.com/pt/about", alternates.get("x-default"));
    }

    @Test
    @DisplayName("buildAlternates: returns empty map for empty locales")
    void buildAlternatesEmptyForNoLocales() {
        assertTrue(resolver.buildAlternates("/about", List.of()).isEmpty());
        assertTrue(resolver.buildAlternates("/about", null).isEmpty());
    }

    @Test
    @DisplayName("buildAlternates: returns unmodifiable map")
    void buildAlternatesReturnsUnmodifiable() {
        Map<String, String> alternates = resolver.buildAlternates("/about", List.of("en", "pt"));

        assertThrows(UnsupportedOperationException.class, () ->
                alternates.put("fr", "https://example.com/fr/about"));
    }
}
