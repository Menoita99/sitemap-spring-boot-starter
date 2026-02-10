package net.menoita.sitemap.controller;

import net.menoita.sitemap.annotation.Sitemap;
import net.menoita.sitemap.annotation.SitemapExclude;
import net.menoita.sitemap.config.SitemapAutoConfiguration;
import net.menoita.sitemap.core.SitemapHolder;
import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.SitemapUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link SitemapController}.
 * Uses {@code @WebMvcTest} to verify the sitemap endpoints return valid XML
 * with the correct content type, annotation scanning, and sitemap index splitting.
 */
@WebMvcTest
@ImportAutoConfiguration(SitemapAutoConfiguration.class)
@TestPropertySource(properties = {
        "sitemap.base-url=https://test.example.com",
        "sitemap.auto-scan=false",
        "sitemap.initialization=eager",
        "sitemap.default-priority=0.5"
})
class SitemapControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SitemapHolder sitemapHolder;

    @Test
    @DisplayName("GET /sitemap.xml returns XML with correct content type")
    void returnsXmlContentType() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    @Test
    @DisplayName("GET /sitemap.xml contains urlset element")
    void containsUrlsetElement() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("</urlset>")));
    }

    @Test
    @DisplayName("GET /sitemap.xml includes annotated endpoints")
    void includesAnnotatedEndpoints() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://test.example.com/public")))
                .andExpect(content().string(containsString("<priority>0.9</priority>")));
    }

    @Test
    @DisplayName("GET /sitemap.xml excludes @SitemapExclude endpoints")
    void excludesSitemapExcludeEndpoints() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/excluded"))));
    }

    @Test
    @DisplayName("GET /sitemap.xml excludes non-annotated endpoints when auto-scan=false")
    void excludesNonAnnotatedEndpoints() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/no-annotation"))));
    }

    @Test
    @DisplayName("Programmatic add() is reflected in sitemap")
    void programmaticAddReflectedInSitemap() throws Exception {
        sitemapHolder.add(SitemapUrl.builder("https://test.example.com/dynamic-page")
                .priority(0.7)
                .changefreq(ChangeFrequency.MONTHLY)
                .build());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://test.example.com/dynamic-page")))
                .andExpect(content().string(containsString("<changefreq>monthly</changefreq>")));
    }

    @Test
    @DisplayName("GET /sitemap-{page}.xml returns 404 for invalid page")
    void returns404ForInvalidPage() throws Exception {
        mockMvc.perform(get("/sitemap-999.xml"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test controller used for integration testing.
     */
    @RestController
    static class TestController {

        @Sitemap(priority = 0.9, changefreq = ChangeFrequency.DAILY)
        @GetMapping("/public")
        public String publicPage() {
            return "public";
        }

        @SitemapExclude
        @Sitemap(priority = 0.5)
        @GetMapping("/excluded")
        public String excludedPage() {
            return "excluded";
        }

        @GetMapping("/no-annotation")
        public String noAnnotation() {
            return "no-annotation";
        }
    }

    @Configuration
    @Import(TestController.class)
    static class TestConfig {
    }
}
