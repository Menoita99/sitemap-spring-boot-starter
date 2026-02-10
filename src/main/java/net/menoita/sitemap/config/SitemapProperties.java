package net.menoita.sitemap.config;

import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.InitializationType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration properties for the sitemap generator library.
 *
 * <p>All properties are prefixed with {@code sitemap.} and can be set in
 * {@code application.properties} or {@code application.yml}.</p>
 *
 * <h3>Minimal configuration</h3>
 * <pre>{@code
 * sitemap:
 *   base-url: https://www.example.com
 * }</pre>
 *
 * <h3>Full configuration example</h3>
 * <pre>{@code
 * sitemap:
 *   enabled: true
 *   base-url: https://www.example.com
 *   auto-scan: true
 *   auto-scan-methods: GET
 *   default-priority: 0.5
 *   default-changefreq: weekly
 *   initialization: eager
 *   max-urls-per-sitemap: 50000
 *   locales: en, pt, es
 *   locale-url-pattern: path_prefix
 *   locale-query-param-name: lang
 *   default-locale: en
 *   omit-default-locale-in-url: false
 * }</pre>
 */
@ConfigurationProperties(prefix = "sitemap")
public class SitemapProperties {

    /**
     * Whether sitemap generation is enabled.
     * When {@code false}, no beans are registered and no endpoints are exposed.
     * Default: {@code true}.
     */
    private boolean enabled = true;

    /**
     * Base URL of the site (e.g. {@code "https://www.example.com"}).
     * <strong>Required.</strong> Used to construct fully qualified URLs for scanned endpoints.
     * Must include the protocol (http/https) and should not end with a trailing slash.
     */
    private String baseUrl;

    /**
     * Whether to automatically scan all controller endpoints matching the configured
     * HTTP methods. When {@code false}, only endpoints annotated with
     * {@link net.menoita.sitemap.annotation.Sitemap @Sitemap} are included.
     * Default: {@code false}.
     */
    private boolean autoScan = false;

    /**
     * HTTP methods to include when auto-scanning controller endpoints.
     * Only endpoints mapped to these methods will be considered.
     * Default: {@code ["GET"]}.
     */
    private Set<String> autoScanMethods = new LinkedHashSet<>(Set.of("GET"));

    /**
     * Default priority assigned to URLs that do not have an explicit priority set.
     * Valid values range from 0.0 to 1.0.
     * Default: {@code 0.5}.
     */
    private double defaultPriority = 0.5;

    /**
     * Default change frequency assigned to URLs that do not have an explicit change
     * frequency set. {@link ChangeFrequency#UNSET} means the {@code <changefreq>}
     * element is omitted from the XML output.
     * Default: {@link ChangeFrequency#UNSET}.
     */
    private ChangeFrequency defaultChangefreq = ChangeFrequency.UNSET;

    /**
     * Controls when the endpoint scanner runs its initial scan.
     * Default: {@link InitializationType#EAGER}.
     *
     * @see InitializationType#EAGER
     * @see InitializationType#LAZY
     */
    private InitializationType initialization = InitializationType.EAGER;

    /**
     * Maximum number of URLs per individual sitemap file.
     * When the total number of URLs exceeds this limit, a sitemap index is generated
     * that references multiple sitemap files.
     * The sitemap protocol defines a maximum of 50,000 URLs per file.
     * Default: {@code 50000}.
     */
    private int maxUrlsPerSitemap = 50_000;

    /**
     * Explicit list of locale codes for hreflang alternate link generation.
     * When non-empty, overrides auto-detection from Spring's {@code LocaleResolver}.
     * Can still be overridden per-endpoint by {@code @Sitemap(locales={...})}.
     * An empty list means "fall back to auto-detection via LocaleResolver".
     * Default: empty list.
     */
    private List<String> locales = new ArrayList<>();

    /**
     * URL pattern strategy for locale URL construction.
     * Default: {@link LocaleUrlPattern#PATH_PREFIX}.
     *
     * @see LocaleUrlPattern
     */
    private LocaleUrlPattern localeUrlPattern = LocaleUrlPattern.PATH_PREFIX;

    /**
     * Query parameter name used when {@link #localeUrlPattern} is {@link LocaleUrlPattern#QUERY_PARAM}.
     * For example, {@code "lang"} produces URLs like {@code ?lang=en}, {@code ?lang=fr}.
     * Default: {@code "lang"}.
     */
    private String localeQueryParamName = "lang";

    /**
     * The default locale code. When set and {@link #omitDefaultLocaleInUrl} is {@code true},
     * the URL for this locale omits the locale identifier.
     * For example, if defaultLocale is {@code "en"}, then {@code https://example.com/about}
     * is used instead of {@code https://example.com/en/about} or {@code https://example.com/about?lang=en}.
     * {@code null} means no default locale — all locales always include the locale identifier.
     * Default: {@code null}.
     */
    private String defaultLocale;

    /**
     * Whether to omit the locale identifier in the URL for the {@link #defaultLocale}.
     * Only has effect when {@link #defaultLocale} is set.
     * <ul>
     *   <li>{@code true}: default locale gets a clean URL (e.g. {@code /about})</li>
     *   <li>{@code false}: all locales include the identifier (e.g. {@code /en/about})</li>
     * </ul>
     * Default: {@code false}.
     */
    private boolean omitDefaultLocaleInUrl = false;

    // --- Getters and Setters ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
    }

    public Set<String> getAutoScanMethods() {
        return autoScanMethods;
    }

    public void setAutoScanMethods(Set<String> autoScanMethods) {
        this.autoScanMethods = autoScanMethods;
    }

    public double getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(double defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public ChangeFrequency getDefaultChangefreq() {
        return defaultChangefreq;
    }

    public void setDefaultChangefreq(ChangeFrequency defaultChangefreq) {
        this.defaultChangefreq = defaultChangefreq;
    }

    public InitializationType getInitialization() {
        return initialization;
    }

    public void setInitialization(InitializationType initialization) {
        this.initialization = initialization;
    }

    public int getMaxUrlsPerSitemap() {
        return maxUrlsPerSitemap;
    }

    public void setMaxUrlsPerSitemap(int maxUrlsPerSitemap) {
        this.maxUrlsPerSitemap = maxUrlsPerSitemap;
    }

    public List<String> getLocales() {
        return locales;
    }

    public void setLocales(List<String> locales) {
        this.locales = locales;
    }

    public LocaleUrlPattern getLocaleUrlPattern() {
        return localeUrlPattern;
    }

    public void setLocaleUrlPattern(LocaleUrlPattern localeUrlPattern) {
        this.localeUrlPattern = localeUrlPattern;
    }

    public String getLocaleQueryParamName() {
        return localeQueryParamName;
    }

    public void setLocaleQueryParamName(String localeQueryParamName) {
        this.localeQueryParamName = localeQueryParamName;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public boolean isOmitDefaultLocaleInUrl() {
        return omitDefaultLocaleInUrl;
    }

    public void setOmitDefaultLocaleInUrl(boolean omitDefaultLocaleInUrl) {
        this.omitDefaultLocaleInUrl = omitDefaultLocaleInUrl;
    }

    /**
     * URL pattern strategy for constructing locale-specific URLs.
     */
    public enum LocaleUrlPattern {

        /**
         * Inserts the locale as a path prefix segment.
         * Example: {@code https://example.com/en/about}
         */
        PATH_PREFIX,

        /**
         * Appends the locale as a query parameter.
         * Example: {@code https://example.com/about?lang=en}
         */
        QUERY_PARAM
    }
}
