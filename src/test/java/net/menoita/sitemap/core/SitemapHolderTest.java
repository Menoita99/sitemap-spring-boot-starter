package net.menoita.sitemap.core;

import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.SitemapUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SitemapHolder}.
 * Tests thread safety, add/remove/clear operations, cache invalidation,
 * and pagination for sitemap index support.
 */
class SitemapHolderTest {

    private SitemapProperties properties;
    private SitemapXmlGenerator xmlGenerator;
    private SitemapHolder holder;

    @BeforeEach
    void setUp() {
        properties = new SitemapProperties();
        properties.setBaseUrl("https://example.com");
        properties.setMaxUrlsPerSitemap(3); // Small limit for testing pagination
        xmlGenerator = new SitemapXmlGenerator(properties);
        holder = new SitemapHolder(properties, xmlGenerator);
    }

    @Test
    @DisplayName("add() stores a URL and it can be retrieved")
    void addStoresUrl() {
        SitemapUrl url = SitemapUrl.builder("https://example.com/page").build();
        holder.add(url);

        assertEquals(1, holder.size());
        assertTrue(holder.contains("https://example.com/page"));
    }

    @Test
    @DisplayName("add() replaces existing URL with same loc")
    void addReplacesExistingUrl() {
        SitemapUrl url1 = SitemapUrl.builder("https://example.com/page").priority(0.5).build();
        SitemapUrl url2 = SitemapUrl.builder("https://example.com/page").priority(0.9).build();

        holder.add(url1);
        holder.add(url2);

        assertEquals(1, holder.size());
        SitemapUrl stored = holder.getUrls().iterator().next();
        assertEquals(0.9, stored.priority());
    }

    @Test
    @DisplayName("addAll() stores multiple URLs at once")
    void addAllStoresMultipleUrls() {
        List<SitemapUrl> urls = List.of(
                SitemapUrl.builder("https://example.com/a").build(),
                SitemapUrl.builder("https://example.com/b").build(),
                SitemapUrl.builder("https://example.com/c").build()
        );
        holder.addAll(urls);

        assertEquals(3, holder.size());
    }

    @Test
    @DisplayName("remove() removes a URL by loc")
    void removeRemovesUrl() {
        holder.add(SitemapUrl.builder("https://example.com/page").build());
        assertTrue(holder.remove("https://example.com/page"));
        assertEquals(0, holder.size());
        assertFalse(holder.contains("https://example.com/page"));
    }

    @Test
    @DisplayName("remove() returns false for non-existent URL")
    void removeReturnsFalseForMissing() {
        assertFalse(holder.remove("https://example.com/nonexistent"));
    }

    @Test
    @DisplayName("clear() removes all URLs")
    void clearRemovesAll() {
        holder.add(SitemapUrl.builder("https://example.com/a").build());
        holder.add(SitemapUrl.builder("https://example.com/b").build());
        holder.clear();

        assertEquals(0, holder.size());
    }

    @Test
    @DisplayName("getUrls() returns unmodifiable collection")
    void getUrlsReturnsUnmodifiable() {
        holder.add(SitemapUrl.builder("https://example.com/page").build());
        assertThrows(UnsupportedOperationException.class, () ->
                holder.getUrls().clear());
    }

    @Test
    @DisplayName("getUrls(page, pageSize) returns correct page")
    void getUrlsPaginates() {
        for (int i = 1; i <= 7; i++) {
            holder.add(SitemapUrl.builder("https://example.com/page" + i).build());
        }

        // maxUrlsPerSitemap = 3, so page sizes of 3
        List<SitemapUrl> page1 = holder.getUrls(1, 3);
        List<SitemapUrl> page2 = holder.getUrls(2, 3);
        List<SitemapUrl> page3 = holder.getUrls(3, 3);
        List<SitemapUrl> page4 = holder.getUrls(4, 3);

        assertEquals(3, page1.size());
        assertEquals(3, page2.size());
        assertEquals(1, page3.size());
        assertTrue(page4.isEmpty());
    }

    @Test
    @DisplayName("getSitemapCount() calculates correct number of sitemaps")
    void getSitemapCountCalculatesCorrectly() {
        assertEquals(0, holder.getSitemapCount());

        for (int i = 1; i <= 7; i++) {
            holder.add(SitemapUrl.builder("https://example.com/p" + i).build());
        }

        // maxUrlsPerSitemap = 3, 7 URLs → ceil(7/3) = 3
        assertEquals(3, holder.getSitemapCount());
    }

    @Test
    @DisplayName("requiresIndex() returns true when URLs exceed max")
    void requiresIndexWhenExceeded() {
        assertFalse(holder.requiresIndex());

        for (int i = 1; i <= 4; i++) {
            holder.add(SitemapUrl.builder("https://example.com/p" + i).build());
        }

        // maxUrlsPerSitemap = 3, 4 URLs → requires index
        assertTrue(holder.requiresIndex());
    }

    @Test
    @DisplayName("getSitemapXml() returns cached XML on subsequent calls")
    void getSitemapXmlReturnsCached() {
        holder.add(SitemapUrl.builder("https://example.com/page").build());

        String xml1 = holder.getSitemapXml();
        String xml2 = holder.getSitemapXml();

        assertSame(xml1, xml2, "Should return same cached instance");
    }

    @Test
    @DisplayName("add() invalidates cached XML")
    void addInvalidatesCache() {
        holder.add(SitemapUrl.builder("https://example.com/page1").build());
        String xml1 = holder.getSitemapXml();

        holder.add(SitemapUrl.builder("https://example.com/page2").build());
        String xml2 = holder.getSitemapXml();

        assertNotSame(xml1, xml2, "Cache should have been invalidated");
        assertTrue(xml2.contains("page2"));
    }

    @Test
    @DisplayName("remove() invalidates cached XML")
    void removeInvalidatesCache() {
        holder.add(SitemapUrl.builder("https://example.com/page1").build());
        holder.add(SitemapUrl.builder("https://example.com/page2").build());
        String xml1 = holder.getSitemapXml();

        holder.remove("https://example.com/page2");
        String xml2 = holder.getSitemapXml();

        assertNotSame(xml1, xml2);
        assertFalse(xml2.contains("page2"));
    }

    @Test
    @DisplayName("clear() invalidates cached XML")
    void clearInvalidatesCache() {
        holder.add(SitemapUrl.builder("https://example.com/page").build());
        holder.getSitemapXml(); // populate cache

        holder.clear();
        String xml = holder.getSitemapXml();

        assertFalse(xml.contains("page"));
    }

    @Test
    @DisplayName("getSitemapXml() includes all URL fields")
    void getSitemapXmlIncludesAllFields() {
        holder.add(SitemapUrl.builder("https://example.com/page")
                .lastmod(LocalDateTime.of(2025, 2, 1, 0, 0))
                .changefreq(ChangeFrequency.WEEKLY)
                .priority(0.8)
                .build());

        String xml = holder.getSitemapXml();

        assertTrue(xml.contains("<loc>https://example.com/page</loc>"));
        assertTrue(xml.contains("<lastmod>2025-02-01</lastmod>"));
        assertTrue(xml.contains("<changefreq>weekly</changefreq>"));
        assertTrue(xml.contains("<priority>0.8</priority>"));
    }

    @Test
    @DisplayName("Thread safety: concurrent adds produce correct size")
    void concurrentAddsAreThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int urlsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < urlsPerThread; i++) {
                        holder.add(SitemapUrl.builder(
                                "https://example.com/t" + threadId + "/p" + i).build());
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "No errors should occur during concurrent access");
        assertEquals(threadCount * urlsPerThread, holder.size());
    }
}
