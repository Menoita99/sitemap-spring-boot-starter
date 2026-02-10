package net.menoita.sitemap.core;

import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.SitemapUrl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Generates sitemap XML strings compliant with the
 * <a href="https://www.sitemaps.org/protocol.html">sitemaps.org protocol</a>.
 *
 * <p>Handles:</p>
 * <ul>
 *   <li>Single sitemap XML ({@code <urlset>}) generation</li>
 *   <li>Sitemap index XML ({@code <sitemapindex>}) generation</li>
 *   <li>Entity escaping ({@code &amp;}, {@code &apos;}, {@code &quot;}, {@code &gt;}, {@code &lt;})</li>
 *   <li>{@code <xhtml:link rel="alternate" hreflang="...">} generation for multilingual URLs</li>
 *   <li>{@code LocalDateTime} formatting to W3C Datetime format</li>
 * </ul>
 *
 * <p>XML is generated using {@link StringBuilder} for maximum performance with zero
 * external dependencies (no JAXB, no DOM).</p>
 */
public class SitemapXmlGenerator {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String URLSET_OPEN = "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"";
    private static final String XHTML_NAMESPACE = "\n        xmlns:xhtml=\"http://www.w3.org/1999/xhtml\"";
    private static final String SITEMAP_INDEX_OPEN =
            "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
    private static final String SITEMAP_INDEX_CLOSE = "</sitemapindex>\n";

    private final SitemapProperties properties;

    /**
     * Constructs a new SitemapXmlGenerator.
     *
     * @param properties the sitemap configuration properties (used for base URL in index generation)
     */
    public SitemapXmlGenerator(SitemapProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a complete sitemap XML document for the given URLs.
     *
     * <p>The output includes the XML declaration, the {@code <urlset>} element with the
     * sitemap namespace, and a {@code <url>} entry for each URL. If any URL has non-empty
     * alternates, the {@code xmlns:xhtml} namespace is automatically added to the
     * {@code <urlset>} element.</p>
     *
     * @param urls the collection of sitemap URL entries to include
     * @return the complete sitemap XML string
     */
    public String generateSitemap(Collection<SitemapUrl> urls) {
        boolean hasAlternates = urls.stream().anyMatch(u -> !u.alternates().isEmpty());

        StringBuilder sb = new StringBuilder(urls.size() * 256);
        sb.append(XML_HEADER).append(URLSET_OPEN);
        if (hasAlternates) {
            sb.append(XHTML_NAMESPACE);
        }
        sb.append(">\n");

        urls.forEach(url -> appendUrl(sb, url));

        sb.append("</urlset>\n");
        return sb.toString();
    }

    /**
     * Generates a sitemap index XML document pointing to individual sitemap files.
     *
     * <p>Each {@code <sitemap>} entry references {@code /sitemap-{n}.xml} on the
     * configured base URL.</p>
     *
     * @param sitemapCount the total number of individual sitemap files
     * @return the sitemap index XML string
     */
    public String generateSitemapIndex(int sitemapCount) {
        String baseUrl = stripTrailingSlash(properties.getBaseUrl());

        StringBuilder sb = new StringBuilder(sitemapCount * 128);
        sb.append(XML_HEADER).append(SITEMAP_INDEX_OPEN);

        IntStream.rangeClosed(1, sitemapCount).forEach(i ->
                sb.append("  <sitemap>\n")
                        .append("    <loc>").append(escapeXml(baseUrl + "/sitemap-" + i + ".xml")).append("</loc>\n")
                        .append("  </sitemap>\n"));

        sb.append(SITEMAP_INDEX_CLOSE);
        return sb.toString();
    }

    /**
     * Appends a single {@code <url>} entry to the StringBuilder, including
     * hreflang {@code <xhtml:link>} alternates when present.
     */
    private void appendUrl(StringBuilder sb, SitemapUrl url) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(url.loc())).append("</loc>\n");

        // hreflang alternate links
        url.alternates().forEach((hreflang, href) ->
                sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"")
                        .append(escapeXml(hreflang))
                        .append("\" href=\"")
                        .append(escapeXml(href))
                        .append("\"/>\n"));

        Optional.ofNullable(url.lastmod())
                .map(SitemapXmlGenerator::formatLastmod)
                .ifPresent(val -> sb.append("    <lastmod>").append(val).append("</lastmod>\n"));

        Optional.ofNullable(url.changefreq())
                .filter(cf -> cf != ChangeFrequency.UNSET)
                .map(ChangeFrequency::getValue)
                .ifPresent(val -> sb.append("    <changefreq>").append(val).append("</changefreq>\n"));

        Optional.ofNullable(url.priority())
                .map(p -> String.format("%.1f", p))
                .ifPresent(val -> sb.append("    <priority>").append(val).append("</priority>\n"));

        sb.append("  </url>\n");
    }

    /**
     * Formats a {@link LocalDateTime} to W3C Datetime format for XML output.
     *
     * <p>If the time component is midnight ({@code 00:00:00}), only the date is emitted
     * (e.g. {@code 2025-02-01}). Otherwise, the full date-time is emitted
     * (e.g. {@code 2025-02-01T10:30:00}).</p>
     */
    static String formatLastmod(LocalDateTime lastmod) {
        return lastmod.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE)
                : lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Escapes XML entities in a string per the sitemap protocol requirements.
     *
     * <p>Escaped characters: {@code & → &amp;}, {@code ' → &apos;},
     * {@code " → &quot;}, {@code > → &gt;}, {@code < → &lt;}</p>
     *
     * @param value the string to escape
     * @return the escaped string, safe for XML output
     */
    static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '\'' -> sb.append("&apos;");
                case '"' -> sb.append("&quot;");
                case '>' -> sb.append("&gt;");
                case '<' -> sb.append("&lt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Strips trailing slash from a URL string. Returns the input unchanged if no trailing slash.
     */
    static String stripTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}
