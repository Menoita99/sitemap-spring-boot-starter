package net.menoita.sitemap.annotation;

import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.SitemapUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Sitemap} annotation attributes and the
 * {@link SitemapUrl} record validation and builder.
 */
class SitemapAnnotationTest {

    // --- @Sitemap annotation defaults ---

    @Test
    @DisplayName("@Sitemap default values are correct")
    void sitemapDefaultValues() throws NoSuchMethodException {
        Sitemap annotation = AnnotatedController.class
                .getMethod("defaultAnnotation")
                .getAnnotation(Sitemap.class);

        assertEquals(-1, annotation.priority());
        assertEquals(ChangeFrequency.UNSET, annotation.changefreq());
        assertEquals("", annotation.lastmod());
        assertEquals(0, annotation.locales().length);
    }

    @Test
    @DisplayName("@Sitemap custom values are preserved")
    void sitemapCustomValues() throws NoSuchMethodException {
        Sitemap annotation = AnnotatedController.class
                .getMethod("customAnnotation")
                .getAnnotation(Sitemap.class);

        assertEquals(0.8, annotation.priority());
        assertEquals(ChangeFrequency.WEEKLY, annotation.changefreq());
        assertEquals("2025-01-15", annotation.lastmod());
        assertArrayEquals(new String[]{"en", "pt"}, annotation.locales());
    }

    @Test
    @DisplayName("@SitemapExclude is present on excluded method")
    void sitemapExcludeIsPresent() throws NoSuchMethodException {
        assertTrue(AnnotatedController.class
                .getMethod("excludedMethod")
                .isAnnotationPresent(SitemapExclude.class));
    }

    @Test
    @DisplayName("@Sitemap can be placed on class level")
    void sitemapOnClass() {
        assertTrue(ClassAnnotatedController.class.isAnnotationPresent(Sitemap.class));
        assertEquals(0.7, ClassAnnotatedController.class
                .getAnnotation(Sitemap.class).priority());
    }

    // --- SitemapUrl validation ---

    @Test
    @DisplayName("SitemapUrl rejects null loc")
    void rejectsNullLoc() {
        assertThrows(NullPointerException.class, () ->
                SitemapUrl.builder(null).build());
    }

    @Test
    @DisplayName("SitemapUrl rejects blank loc")
    void rejectsBlankLoc() {
        assertThrows(IllegalArgumentException.class, () ->
                SitemapUrl.builder("").build());
    }

    @Test
    @DisplayName("SitemapUrl rejects loc without protocol")
    void rejectsLocWithoutProtocol() {
        assertThrows(IllegalArgumentException.class, () ->
                SitemapUrl.builder("example.com/page").build());
    }

    @Test
    @DisplayName("SitemapUrl rejects priority out of range")
    void rejectsPriorityOutOfRange() {
        assertThrows(IllegalArgumentException.class, () ->
                SitemapUrl.builder("https://example.com").priority(1.5).build());
        assertThrows(IllegalArgumentException.class, () ->
                SitemapUrl.builder("https://example.com").priority(-0.1).build());
    }

    @Test
    @DisplayName("SitemapUrl builder creates valid record")
    void builderCreatesValidRecord() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .priority(0.8)
                .changefreq(ChangeFrequency.DAILY)
                .lastmod(LocalDateTime.of(2025, 2, 1, 0, 0))
                .alternate("en", "https://example.com/en/page")
                .build();

        assertEquals("https://example.com/page", url.loc());
        assertEquals(0.8, url.priority());
        assertEquals(ChangeFrequency.DAILY, url.changefreq());
        assertEquals(LocalDateTime.of(2025, 2, 1, 0, 0), url.lastmod());
        assertEquals(1, url.alternates().size());
    }

    @Test
    @DisplayName("SitemapUrl alternates map is unmodifiable")
    void alternatesMapIsUnmodifiable() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .alternate("en", "https://example.com/en/page")
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                url.alternates().put("fr", "https://example.com/fr/page"));
    }

    @Test
    @DisplayName("SitemapUrl alternates builder replaces with alternates(Map)")
    void alternatesBuilderReplaces() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page")
                .alternate("en", "https://example.com/en/page")
                .alternates(Map.of("fr", "https://example.com/fr/page"))
                .build();

        assertEquals(1, url.alternates().size());
        assertTrue(url.alternates().containsKey("fr"));
        assertFalse(url.alternates().containsKey("en"));
    }

    // --- Test fixtures ---

    static class AnnotatedController {
        @Sitemap
        public void defaultAnnotation() {}

        @Sitemap(priority = 0.8, changefreq = ChangeFrequency.WEEKLY,
                lastmod = "2025-01-15", locales = {"en", "pt"})
        public void customAnnotation() {}

        @SitemapExclude
        public void excludedMethod() {}
    }

    @Sitemap(priority = 0.7)
    static class ClassAnnotatedController {
    }
}
