package net.menoita.sitemap.config;

import net.menoita.sitemap.controller.SitemapController;
import net.menoita.sitemap.core.SitemapEndpointScanner;
import net.menoita.sitemap.core.SitemapHolder;
import net.menoita.sitemap.core.SitemapLocaleResolver;
import net.menoita.sitemap.core.SitemapXmlGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Boot auto-configuration for the sitemap generator library.
 *
 * <p>This configuration is activated when:</p>
 * <ul>
 *   <li>{@code sitemap.enabled=true} (default, matches if missing)</li>
 * </ul>
 *
 * <p>It registers the following beans:</p>
 * <ul>
 *   <li>{@link SitemapXmlGenerator} — generates sitemap XML strings</li>
 *   <li>{@link SitemapLocaleResolver} — resolves locales for hreflang generation</li>
 *   <li>{@link SitemapHolder} — thread-safe singleton holding all sitemap URLs</li>
 *   <li>{@link SitemapEndpointScanner} — scans controller endpoints for sitemap registration</li>
 *   <li>{@link SitemapController} — serves {@code /sitemap.xml} and {@code /sitemap-{n}.xml}</li>
 * </ul>
 *
 * <p>All beans are {@code @ConditionalOnMissingBean}, allowing applications to provide
 * custom implementations if needed.</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sitemap", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SitemapProperties.class)
public class SitemapAutoConfiguration {

    /**
     * Creates the XML generator bean.
     *
     * @param properties the sitemap configuration properties
     * @return a new SitemapXmlGenerator instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SitemapXmlGenerator sitemapXmlGenerator(SitemapProperties properties) {
        return new SitemapXmlGenerator(properties);
    }

    /**
     * Creates the locale resolver bean for sitemap hreflang generation.
     *
     * @param properties the sitemap configuration properties
     * @return a new SitemapLocaleResolver instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SitemapLocaleResolver sitemapLocaleResolver(SitemapProperties properties) {
        return new SitemapLocaleResolver(properties);
    }

    /**
     * Creates the central sitemap holder bean (singleton).
     *
     * @param properties   the sitemap configuration properties
     * @param xmlGenerator the XML generator
     * @return a new SitemapHolder instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SitemapHolder sitemapHolder(SitemapProperties properties, SitemapXmlGenerator xmlGenerator) {
        return new SitemapHolder(properties, xmlGenerator);
    }

    /**
     * Creates the endpoint scanner bean.
     *
     * @param handlerMapping the Spring MVC handler mapping
     * @param sitemapHolder  the sitemap holder to register URLs in
     * @param properties     the sitemap configuration properties
     * @param localeResolver the sitemap locale resolver
     * @return a new SitemapEndpointScanner instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SitemapEndpointScanner sitemapEndpointScanner(
            RequestMappingHandlerMapping handlerMapping,
            SitemapHolder sitemapHolder,
            SitemapProperties properties,
            SitemapLocaleResolver localeResolver) {
        return new SitemapEndpointScanner(handlerMapping, sitemapHolder, properties, localeResolver);
    }

    /**
     * Creates the sitemap controller bean that serves the XML endpoints.
     *
     * @param sitemapHolder    the sitemap holder
     * @param endpointScanner  the endpoint scanner (for lazy initialization support)
     * @param properties       the sitemap configuration properties
     * @return a new SitemapController instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SitemapController sitemapController(
            SitemapHolder sitemapHolder,
            SitemapEndpointScanner endpointScanner,
            SitemapProperties properties) {
        return new SitemapController(sitemapHolder, endpointScanner, properties);
    }
}
