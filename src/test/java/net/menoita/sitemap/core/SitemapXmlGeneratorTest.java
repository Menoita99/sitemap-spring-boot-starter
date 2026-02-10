package net.menoita.sitemap.core;

import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.SitemapUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SitemapXmlGenerator}.
 * Tests valid XML output, entity escaping, sitemap index generation,
 * hreflang xhtml:link output, and LocalDateTime formatting.
 */
class SitemapXmlGeneratorTest {

    private SitemapProperties properties;
    private SitemapXmlGenerator generator;

    @BeforeEach
    void setUp() {
        properties = new SitemapProperties();
        properties.setBaseUrl("https://example.com");
        generator = new SitemapXmlGenerator(properties);
    }

    @Test
    @DisplayName("generates valid XML with urlset namespace")
    void generatesValidUrlset() {
        String xml = generator.generateSitemap(List.of(
                SitemapUrl.builder("https://example.com/page").build()
        ));

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\""));
        assertTrue(xml.contains("<urlset"));
        assertTrue(xml.contains("</urlset>"));
    }

    @Test
    @DisplayName("generates url entry with all fields")
    void generatesUrlWithAllFields() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .lastmod(LocalDateTime.of(2025, 2, 1, 10, 30, 0))
                .changefreq(ChangeFrequency.DAILY)
                .priority(0.8)
                .build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("<loc>https://example.com/page</loc>"));
        assertTrue(xml.contains("<lastmod>2025-02-01T10:30:00</lastmod>"));
        assertTrue(xml.contains("<changefreq>daily</changefreq>"));
        assertTrue(xml.contains("<priority>0.8</priority>"));
    }

    @Test
    @DisplayName("omits optional fields when not set")
    void omitsOptionalFields() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page").build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("<loc>https://example.com/page</loc>"));
        assertFalse(xml.contains("<lastmod>"));
        assertFalse(xml.contains("<changefreq>"));
        assertFalse(xml.contains("<priority>"));
    }

    @Test
    @DisplayName("omits changefreq when UNSET")
    void omitsChangefreqWhenUnset() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .changefreq(ChangeFrequency.UNSET)
                .build();

        String xml = generator.generateSitemap(List.of(url));

        assertFalse(xml.contains("<changefreq>"));
    }

    @Test
    @DisplayName("formats date-only lastmod as YYYY-MM-DD")
    void formatsDateOnlyLastmod() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .lastmod(LocalDateTime.of(2025, 1, 15, 0, 0))
                .build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("<lastmod>2025-01-15</lastmod>"));
    }

    @Test
    @DisplayName("formats date-time lastmod as ISO_LOCAL_DATE_TIME")
    void formatsDateTimeLastmod() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .lastmod(LocalDateTime.of(2025, 1, 15, 14, 30, 45))
                .build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("<lastmod>2025-01-15T14:30:45</lastmod>"));
    }

    @Test
    @DisplayName("escapes XML entities in URLs")
    void escapesXmlEntities() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page?a=1&b=2").build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("<loc>https://example.com/page?a=1&amp;b=2</loc>"));
        assertFalse(xml.contains("<loc>https://example.com/page?a=1&b=2</loc>"));
    }

    @Test
    @DisplayName("escapeXml handles all special characters")
    void escapeXmlHandlesAllSpecialChars() {
        assertEquals("&amp;", SitemapXmlGenerator.escapeXml("&"));
        assertEquals("&apos;", SitemapXmlGenerator.escapeXml("'"));
        assertEquals("&quot;", SitemapXmlGenerator.escapeXml("\""));
        assertEquals("&gt;", SitemapXmlGenerator.escapeXml(">"));
        assertEquals("&lt;", SitemapXmlGenerator.escapeXml("<"));
        assertEquals("normal text", SitemapXmlGenerator.escapeXml("normal text"));
        assertEquals("", SitemapXmlGenerator.escapeXml(null));
    }

    @Test
    @DisplayName("generates sitemap index with correct structure")
    void generatesSitemapIndex() {
        String xml = generator.generateSitemapIndex(3);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"));
        assertTrue(xml.contains("<loc>https://example.com/sitemap-1.xml</loc>"));
        assertTrue(xml.contains("<loc>https://example.com/sitemap-2.xml</loc>"));
        assertTrue(xml.contains("<loc>https://example.com/sitemap-3.xml</loc>"));
        assertTrue(xml.contains("</sitemapindex>"));
    }

    @Test
    @DisplayName("generates hreflang alternate links when alternates present")
    void generatesHreflangAlternates() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/en/about")
                .alternate("en", "https://example.com/en/about")
                .alternate("pt", "https://example.com/pt/about")
                .alternate("x-default", "https://example.com/en/about")
                .build();

        String xml = generator.generateSitemap(List.of(url));

        // Should include xhtml namespace
        assertTrue(xml.contains("xmlns:xhtml=\"http://www.w3.org/1999/xhtml\""));

        // Should include alternate links
        assertTrue(xml.contains(
                "<xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"https://example.com/en/about\"/>"));
        assertTrue(xml.contains(
                "<xhtml:link rel=\"alternate\" hreflang=\"pt\" href=\"https://example.com/pt/about\"/>"));
        assertTrue(xml.contains(
                "<xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"https://example.com/en/about\"/>"));
    }

    @Test
    @DisplayName("does not include xhtml namespace when no alternates")
    void noXhtmlNamespaceWithoutAlternates() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page").build();

        String xml = generator.generateSitemap(List.of(url));

        assertFalse(xml.contains("xmlns:xhtml"));
        assertFalse(xml.contains("xhtml:link"));
    }

    @Test
    @DisplayName("formats priority with at least one decimal place")
    void formatsPriorityCorrectly() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .priority(1.0)
                .build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("<priority>1.0</priority>"));
    }

    @Test
    @DisplayName("generates valid XML for empty URL collection")
    void generatesValidXmlForEmptyCollection() {
        String xml = generator.generateSitemap(List.of());

        assertTrue(xml.contains("<urlset"));
        assertTrue(xml.contains("</urlset>"));
    }

    @Test
    @DisplayName("normalizes base URL with trailing slash in sitemap index")
    void normalizesBaseUrlTrailingSlash() {
        properties.setBaseUrl("https://example.com/");
        SitemapXmlGenerator gen = new SitemapXmlGenerator(properties);

        String xml = gen.generateSitemapIndex(2);

        assertTrue(xml.contains("<loc>https://example.com/sitemap-1.xml</loc>"));
        assertFalse(xml.contains("//sitemap-1.xml"));
    }

    @Test
    @DisplayName("entity escapes URLs in alternate links")
    void escapesUrlsInAlternateLinks() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page?a=1&b=2")
                .alternate("en", "https://example.com/page?a=1&b=2&lang=en")
                .build();

        String xml = generator.generateSitemap(List.of(url));

        assertTrue(xml.contains("href=\"https://example.com/page?a=1&amp;b=2&amp;lang=en\""));
    }

    @Test
    @DisplayName("generates multiple URL entries in correct order")
    void generatesMultipleUrlEntries() {
        List<SitemapUrl> urls = List.of(
                SitemapUrl.builder("https://example.com/a").priority(1.0).build(),
                SitemapUrl.builder("https://example.com/b").priority(0.5).build()
        );

        String xml = generator.generateSitemap(urls);

        int posA = xml.indexOf("https://example.com/a");
        int posB = xml.indexOf("https://example.com/b");
        assertTrue(posA > 0 && posB > 0);
        assertTrue(posA < posB, "URLs should appear in collection order");
    }
}
