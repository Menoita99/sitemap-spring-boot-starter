package net.menoita.sitemap.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a single {@code <url>} entry in a sitemap,
 * following the <a href="https://www.sitemaps.org/protocol.html">sitemaps.org protocol</a>.
 *
 * <p>Instances are created via the {@link Builder} to ensure validity and ergonomic construction.
 * The {@code loc} field is required; all other fields are optional.</p>
 *
 * <p>The {@code alternates} map holds hreflang alternate links for multilingual sites.
 * Each entry maps a language/region code (e.g. {@code "en"}, {@code "pt"}, {@code "x-default"})
 * to a fully qualified URL. When non-empty, the XML generator emits
 * {@code <xhtml:link rel="alternate" hreflang="..." href="..."/>} elements.</p>
 *
 * @param loc        Required. The URL of the page (must be fully qualified with protocol).
 * @param lastmod    Optional. Last modification date, formatted to W3C Datetime on XML output.
 * @param changefreq Optional. Hint for how frequently the page changes.
 * @param priority   Optional. Priority relative to other URLs on the site (0.0 to 1.0).
 * @param alternates Optional. Map of hreflang code to alternate URL for multilingual support.
 */
public record SitemapUrl(
        String loc,
        LocalDateTime lastmod,
        ChangeFrequency changefreq,
        Double priority,
        Map<String, String> alternates
) {

    /**
     * Compact constructor that validates inputs and defensively copies the alternates map.
     *
     * @throws NullPointerException     if {@code loc} is null
     * @throws IllegalArgumentException if {@code loc} is blank, does not start with http:// or https://,
     *                                  or if {@code priority} is outside the 0.0–1.0 range
     */
    public SitemapUrl {
        Objects.requireNonNull(loc, "loc must not be null");
        if (loc.isBlank()) {
            throw new IllegalArgumentException("loc must not be blank");
        }
        if (!loc.startsWith("http://") && !loc.startsWith("https://")) {
            throw new IllegalArgumentException("loc must start with http:// or https://, got: " + loc);
        }
        if (priority != null && (priority < 0.0 || priority > 1.0)) {
            throw new IllegalArgumentException("priority must be between 0.0 and 1.0, got: " + priority);
        }
        alternates = alternates == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(alternates));
    }

    /**
     * Creates a new {@link Builder} for constructing a {@code SitemapUrl}.
     *
     * @param loc the required URL of the page
     * @return a new builder instance
     */
    public static Builder builder(String loc) {
        return new Builder(loc);
    }

    /**
     * Builder for ergonomic construction of {@link SitemapUrl} instances.
     *
     * <p>Usage example:</p>
     * <pre>{@code
     * SitemapUrl url = SitemapUrl.builder("https://example.com/page")
     *     .priority(0.8)
     *     .changefreq(ChangeFrequency.WEEKLY)
     *     .lastmod(LocalDateTime.of(2025, 2, 1, 0, 0))
     *     .alternate("en", "https://example.com/en/page")
     *     .alternate("pt", "https://example.com/pt/page")
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private final String loc;
        private LocalDateTime lastmod;
        private ChangeFrequency changefreq;
        private Double priority;
        private final Map<String, String> alternates = new LinkedHashMap<>();

        private Builder(String loc) {
            this.loc = loc;
        }

        /**
         * Sets the last modification date.
         *
         * @param lastmod the last modification date
         * @return this builder
         */
        public Builder lastmod(LocalDateTime lastmod) {
            this.lastmod = lastmod;
            return this;
        }

        /**
         * Sets the change frequency hint.
         *
         * @param changefreq how frequently the page is likely to change
         * @return this builder
         */
        public Builder changefreq(ChangeFrequency changefreq) {
            this.changefreq = changefreq;
            return this;
        }

        /**
         * Sets the priority of this URL relative to other URLs on the site.
         *
         * @param priority value between 0.0 and 1.0
         * @return this builder
         */
        public Builder priority(double priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Adds an hreflang alternate link.
         *
         * @param hreflang the language/region code (e.g. "en", "pt", "x-default")
         * @param href     the fully qualified alternate URL
         * @return this builder
         */
        public Builder alternate(String hreflang, String href) {
            this.alternates.put(hreflang, href);
            return this;
        }

        /**
         * Sets all hreflang alternates at once, replacing any previously added.
         *
         * @param alternates map of hreflang code to URL
         * @return this builder
         */
        public Builder alternates(Map<String, String> alternates) {
            this.alternates.clear();
            if (alternates != null) {
                this.alternates.putAll(alternates);
            }
            return this;
        }

        /**
         * Builds and returns the {@link SitemapUrl} instance.
         *
         * @return a new validated SitemapUrl
         * @throws NullPointerException     if loc is null
         * @throws IllegalArgumentException if loc is invalid or priority is out of range
         */
        public SitemapUrl build() {
            return new SitemapUrl(loc, lastmod, changefreq, priority, alternates);
        }
    }
}
