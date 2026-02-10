package net.menoita.sitemap.controller;

import net.menoita.sitemap.core.SitemapEndpointScanner;
import net.menoita.sitemap.core.SitemapHolder;
import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.model.InitializationType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes sitemap endpoints.
 *
 * <p>Automatically registered by the {@link net.menoita.sitemap.config.SitemapAutoConfiguration}.
 * Serves the generated sitemap XML with the correct {@code application/xml} content type.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /sitemap.xml} — returns a single sitemap or a sitemap index
 *       (if the number of URLs exceeds {@code sitemap.max-urls-per-sitemap})</li>
 *   <li>{@code GET /sitemap-{page}.xml} — returns an individual sitemap page
 *       when in sitemap index mode</li>
 * </ul>
 *
 * <h3>Lazy initialization</h3>
 * <p>When {@code sitemap.initialization=lazy}, the first request to {@code /sitemap.xml}
 * triggers the endpoint scan before returning the XML.</p>
 */
@RestController
public class SitemapController {

    private final SitemapHolder sitemapHolder;
    private final SitemapEndpointScanner endpointScanner;
    private final SitemapProperties properties;

    /**
     * Constructs a new SitemapController.
     *
     * @param sitemapHolder    the holder containing all sitemap URLs
     * @param endpointScanner  the endpoint scanner for lazy initialization
     * @param properties       the sitemap configuration properties
     */
    public SitemapController(SitemapHolder sitemapHolder,
                             SitemapEndpointScanner endpointScanner,
                             SitemapProperties properties) {
        this.sitemapHolder = sitemapHolder;
        this.endpointScanner = endpointScanner;
        this.properties = properties;
    }

    /**
     * Returns the sitemap XML or sitemap index XML.
     *
     * <p>If the total number of URLs exceeds {@code sitemap.max-urls-per-sitemap},
     * a sitemap index is returned that references individual sitemap pages at
     * {@code /sitemap-{n}.xml}. Otherwise, a single sitemap with all URLs is returned.</p>
     *
     * <p>If lazy initialization is configured and the scan has not yet been performed,
     * it is triggered before generating the response.</p>
     *
     * @return the sitemap or sitemap index XML with content-type {@code application/xml}
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        ensureScanned();

        if (sitemapHolder.requiresIndex()) {
            return ResponseEntity.ok(sitemapHolder.getSitemapIndexXml());
        }
        return ResponseEntity.ok(sitemapHolder.getSitemapXml());
    }

    /**
     * Returns an individual sitemap page for sitemap index mode.
     *
     * <p>Pages are 1-indexed. Returns 404 if the page number is out of range.</p>
     *
     * @param page the 1-based sitemap page number
     * @return the sitemap XML for the requested page, or 404 if out of range
     */
    @GetMapping(value = "/sitemap-{page:\\d+}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapPage(@PathVariable int page) {
        ensureScanned();

        if (page < 1 || page > sitemapHolder.getSitemapCount()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(sitemapHolder.getSitemapXml(page));
    }

    /**
     * Ensures the endpoint scan has been performed. In lazy mode, triggers the scan
     * on the first request.
     */
    private void ensureScanned() {
        if (properties.getInitialization() == InitializationType.LAZY && !endpointScanner.isScanned()) {
            endpointScanner.scan();
        }
    }
}
