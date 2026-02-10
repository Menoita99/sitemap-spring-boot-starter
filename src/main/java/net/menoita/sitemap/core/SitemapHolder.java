package net.menoita.sitemap.core;

import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.model.SitemapUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe singleton that holds all sitemap URLs in memory.
 *
 * <p>This is the central component of the sitemap generator library. It is managed
 * as a Spring bean (singleton scope) and acts as the single source of truth for
 * all URLs that should appear in the generated sitemap.</p>
 *
 * <h3>Thread Safety</h3>
 * <ul>
 *   <li>URL storage uses a {@link ConcurrentHashMap} keyed by the URL's {@code loc} value,
 *       ensuring safe concurrent reads and writes.</li>
 *   <li>The generated XML is cached in a {@code volatile} field. This guarantees that when
 *       one thread invalidates the cache (sets it to {@code null} after a mutation), all other
 *       threads immediately see the invalidation and will trigger regeneration rather than
 *       serving stale XML.</li>
 *   <li>A {@link ReentrantReadWriteLock} guards XML cache generation so that only one thread
 *       regenerates the cache at a time while concurrent readers wait, then share the result.</li>
 * </ul>
 *
 * <h3>Programmatic Usage</h3>
 * <pre>{@code
 * @Component
 * public class BlogSitemapPopulator implements CommandLineRunner {
 *     private final SitemapHolder sitemapHolder;
 *
 *     public void run(String... args) {
 *         repository.findAllSlugs().forEach(slug ->
 *             sitemapHolder.add(SitemapUrl.builder("https://example.com/blog/" + slug)
 *                 .priority(0.7)
 *                 .build())
 *         );
 *     }
 * }
 * }</pre>
 */
public class SitemapHolder {

    private static final Logger log = LoggerFactory.getLogger(SitemapHolder.class);

    private final ConcurrentHashMap<String, SitemapUrl> urls = new ConcurrentHashMap<>();
    private final SitemapProperties properties;
    private final SitemapXmlGenerator xmlGenerator;

    /**
     * Volatile cached XML strings. Set to {@code null} on any mutation to force regeneration.
     * The volatile keyword ensures cross-thread visibility: when Thread A calls {@code add()}
     * and sets this to {@code null}, Thread B handling a request immediately sees the change
     * rather than reading a stale value from its CPU cache.
     */
    private volatile String cachedSitemapXml;
    private volatile String cachedSitemapIndexXml;

    /** Lock guarding XML cache regeneration to prevent thundering herd. */
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    /**
     * Constructs a new SitemapHolder.
     *
     * @param properties   the sitemap configuration properties
     * @param xmlGenerator the XML generator used to produce sitemap XML
     */
    public SitemapHolder(SitemapProperties properties, SitemapXmlGenerator xmlGenerator) {
        this.properties = properties;
        this.xmlGenerator = xmlGenerator;
    }

    /**
     * Adds a single URL entry to the sitemap. If a URL with the same {@code loc}
     * already exists, it is replaced. Invalidates the cached XML.
     *
     * @param url the sitemap URL entry to add
     * @throws NullPointerException if url is null
     */
    public void add(SitemapUrl url) {
        urls.put(url.loc(), url);
        invalidateCache();
        log.debug("Added sitemap URL: {}", url.loc());
    }

    /**
     * Adds multiple URL entries to the sitemap. Existing entries with the same
     * {@code loc} are replaced. The cache is invalidated once after all entries are added.
     *
     * @param urls the collection of sitemap URL entries to add
     */
    public void addAll(Collection<SitemapUrl> urls) {
        urls.forEach(url -> this.urls.put(url.loc(), url));
        invalidateCache();
        log.debug("Added {} sitemap URLs", urls.size());
    }

    /**
     * Removes a URL by its location string.
     *
     * @param loc the fully qualified URL to remove
     * @return {@code true} if the URL existed and was removed, {@code false} otherwise
     */
    public boolean remove(String loc) {
        boolean removed = urls.remove(loc) != null;
        if (removed) {
            invalidateCache();
            log.debug("Removed sitemap URL: {}", loc);
        }
        return removed;
    }

    /**
     * Removes all URLs from the sitemap. Invalidates the cached XML.
     */
    public void clear() {
        urls.clear();
        invalidateCache();
        log.debug("Cleared all sitemap URLs");
    }

    /**
     * Checks if a URL location is already registered in the sitemap.
     *
     * @param loc the fully qualified URL to check
     * @return {@code true} if the URL is registered
     */
    public boolean contains(String loc) {
        return urls.containsKey(loc);
    }

    /**
     * Returns the total number of registered URLs.
     *
     * @return the number of URLs in the sitemap
     */
    public int size() {
        return urls.size();
    }

    /**
     * Returns an unmodifiable view of all registered URLs.
     *
     * @return unmodifiable collection of all sitemap URL entries
     */
    public Collection<SitemapUrl> getUrls() {
        return Collections.unmodifiableCollection(urls.values());
    }

    /**
     * Returns a paginated subset of URLs for sitemap index splitting.
     * Pages are 1-indexed.
     *
     * @param page     the 1-based page number
     * @param pageSize the number of URLs per page
     * @return a list of URLs for the requested page, or empty if the page is out of range
     */
    public List<SitemapUrl> getUrls(int page, int pageSize) {
        List<SitemapUrl> allUrls = new ArrayList<>(urls.values());
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= allUrls.size()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                allUrls.subList(fromIndex, Math.min(fromIndex + pageSize, allUrls.size())));
    }

    /**
     * Returns the number of individual sitemap files needed based on the
     * configured {@code maxUrlsPerSitemap}.
     *
     * @return the number of sitemap files (0 if no URLs, otherwise at least 1)
     */
    public int getSitemapCount() {
        int total = urls.size();
        if (total == 0) return 0;
        int max = properties.getMaxUrlsPerSitemap();
        return (total + max - 1) / max;
    }

    /**
     * Returns whether the sitemap requires index mode (more than one sitemap file).
     *
     * @return {@code true} if the number of URLs exceeds {@code maxUrlsPerSitemap}
     */
    public boolean requiresIndex() {
        return urls.size() > properties.getMaxUrlsPerSitemap();
    }

    /**
     * Returns the generated sitemap XML for a single sitemap (all URLs).
     * The result is cached and only regenerated when the URL set has been mutated.
     * Uses double-checked locking with volatile + ReentrantReadWriteLock.
     *
     * @return the complete sitemap XML string
     */
    public String getSitemapXml() {
        String cached = cachedSitemapXml;
        if (cached != null) return cached;

        cacheLock.writeLock().lock();
        try {
            cached = cachedSitemapXml;
            if (cached != null) return cached;
            cached = xmlGenerator.generateSitemap(urls.values());
            cachedSitemapXml = cached;
            return cached;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Returns the generated sitemap XML for a specific page in sitemap index mode.
     * Paged sitemaps are not cached individually to avoid memory bloat.
     *
     * @param page the 1-based page number
     * @return the sitemap XML for the requested page
     */
    public String getSitemapXml(int page) {
        return xmlGenerator.generateSitemap(getUrls(page, properties.getMaxUrlsPerSitemap()));
    }

    /**
     * Returns the generated sitemap index XML.
     * The result is cached and only regenerated when the URL set has been mutated.
     *
     * @return the sitemap index XML string
     */
    public String getSitemapIndexXml() {
        String cached = cachedSitemapIndexXml;
        if (cached != null) return cached;

        cacheLock.writeLock().lock();
        try {
            cached = cachedSitemapIndexXml;
            if (cached != null) return cached;
            cached = xmlGenerator.generateSitemapIndex(getSitemapCount());
            cachedSitemapIndexXml = cached;
            return cached;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /** Invalidates all cached XML strings, forcing regeneration on next access. */
    private void invalidateCache() {
        cachedSitemapXml = null;
        cachedSitemapIndexXml = null;
    }
}
