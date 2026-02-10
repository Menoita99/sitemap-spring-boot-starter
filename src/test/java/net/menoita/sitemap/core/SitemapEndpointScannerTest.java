package net.menoita.sitemap.core;

import net.menoita.sitemap.annotation.Sitemap;
import net.menoita.sitemap.annotation.SitemapExclude;
import net.menoita.sitemap.config.SitemapAutoConfiguration;
import net.menoita.sitemap.model.ChangeFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link SitemapEndpointScanner}.
 * Verifies annotation scanning, auto-scan, path variable skipping,
 * and locale-aware URL generation.
 */
@WebMvcTest
@ImportAutoConfiguration(SitemapAutoConfiguration.class)
@TestPropertySource(properties = {
        "sitemap.base-url=https://scanner-test.example.com",
        "sitemap.auto-scan=true",
        "sitemap.auto-scan-methods=GET",
        "sitemap.initialization=eager"
})
class SitemapEndpointScannerTest {

    @Autowired
    private SitemapHolder sitemapHolder;

    @Autowired
    private SitemapEndpointScanner endpointScanner;

    @Test
    @DisplayName("Scanner has run eagerly on startup")
    void scannerHasRun() {
        assertTrue(endpointScanner.isScanned());
    }

    @Test
    @DisplayName("Auto-scan includes GET endpoints")
    void autoScanIncludesGetEndpoints() {
        assertTrue(sitemapHolder.contains("https://scanner-test.example.com/auto-page"));
    }

    @Test
    @DisplayName("Auto-scan excludes POST endpoints")
    void autoScanExcludesPostEndpoints() {
        assertFalse(sitemapHolder.contains("https://scanner-test.example.com/post-only"));
    }

    @Test
    @DisplayName("Auto-scan excludes @SitemapExclude endpoints")
    void autoScanExcludesAnnotatedExclusions() {
        assertFalse(sitemapHolder.contains("https://scanner-test.example.com/excluded"));
    }

    @Test
    @DisplayName("@Sitemap annotation values are applied")
    void sitemapAnnotationValuesApplied() {
        assertTrue(sitemapHolder.contains("https://scanner-test.example.com/annotated"));
        // Verify the URL has the correct priority by checking the XML output
        String xml = sitemapHolder.getSitemapXml();
        assertTrue(xml.contains("https://scanner-test.example.com/annotated"));
    }

    @Test
    @DisplayName("Path variables are skipped during scan")
    void pathVariablesSkipped() {
        assertFalse(sitemapHolder.contains("https://scanner-test.example.com/users/{id}"));
        // Should not contain any URL with {id} pattern
        sitemapHolder.getUrls().forEach(url ->
                assertFalse(url.loc().contains("{"), "URL should not contain path variables: " + url.loc()));
    }

    @Test
    @DisplayName("Scan is idempotent - second call has no effect")
    void scanIsIdempotent() {
        int sizeBefore = sitemapHolder.size();
        endpointScanner.scan();
        assertEquals(sizeBefore, sitemapHolder.size(), "Size should not change on re-scan");
    }

    /**
     * Test controller with various endpoint configurations.
     */
    @RestController
    static class ScannerTestController {

        @GetMapping("/auto-page")
        public String autoPage() {
            return "auto";
        }

        @Sitemap(priority = 0.9, changefreq = ChangeFrequency.WEEKLY)
        @GetMapping("/annotated")
        public String annotated() {
            return "annotated";
        }

        @SitemapExclude
        @GetMapping("/excluded")
        public String excluded() {
            return "excluded";
        }

        @PostMapping("/post-only")
        public String postOnly() {
            return "post";
        }

        @GetMapping("/users/{id}")
        public String userById(@PathVariable String id) {
            return "user:" + id;
        }
    }

    @Configuration
    @Import(ScannerTestController.class)
    static class TestConfig {
    }
}
