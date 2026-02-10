package net.menoita.sitemap.model;

/**
 * Represents the possible values for the {@code <changefreq>} XML element
 * in a sitemap, as defined by the <a href="https://www.sitemaps.org/protocol.html">sitemaps.org protocol</a>.
 *
 * <p>Each value provides a hint to search engine crawlers about how frequently
 * a page is likely to change. The {@link #UNSET} value indicates that no
 * {@code <changefreq>} element should be emitted in the XML output.</p>
 */
public enum ChangeFrequency {

    /** Describes documents that change each time they are accessed. */
    ALWAYS,

    /** Pages that change every hour. */
    HOURLY,

    /** Pages that change every day. */
    DAILY,

    /** Pages that change every week. */
    WEEKLY,

    /** Pages that change every month. */
    MONTHLY,

    /** Pages that change every year. */
    YEARLY,

    /** Archived URLs that will never change. */
    NEVER,

    /**
     * Sentinel value meaning "do not include {@code <changefreq>} in the XML output".
     * Used as a default when no change frequency is explicitly specified.
     */
    UNSET;

    /**
     * Returns the lowercase string value suitable for XML output.
     * For example, {@code DAILY} returns {@code "daily"}.
     *
     * @return lowercase name of this frequency, or an empty string for {@link #UNSET}
     */
    public String getValue() {
        if (this == UNSET) {
            return "";
        }
        return name().toLowerCase();
    }
}
