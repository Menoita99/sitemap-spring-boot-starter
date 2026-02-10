package net.menoita.sitemap.model;

/**
 * Controls when the {@code SitemapEndpointScanner} performs its initial scan
 * of controller endpoints.
 *
 * <p>Configured via the {@code sitemap.initialization} property.</p>
 */
public enum InitializationType {

    /**
     * Scan all endpoints at application startup (on {@code ApplicationReadyEvent}).
     * The sitemap is fully populated before the first request arrives.
     */
    EAGER,

    /**
     * Defer scanning until the first request to {@code /sitemap.xml}.
     * Useful for applications where startup time is critical and the sitemap
     * can afford a small delay on the first request.
     */
    LAZY
}
