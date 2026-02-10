package net.menoita.sitemap.core;

import net.menoita.sitemap.annotation.Sitemap;
import net.menoita.sitemap.annotation.SitemapExclude;
import net.menoita.sitemap.config.SitemapProperties;
import net.menoita.sitemap.model.ChangeFrequency;
import net.menoita.sitemap.model.InitializationType;
import net.menoita.sitemap.model.SitemapUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import org.springframework.web.bind.annotation.RequestMethod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scans all registered Spring MVC handler methods (from {@code @Controller}/{@code @RestController}
 * beans) and registers matching endpoints in the {@link SitemapHolder}.
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li><strong>Auto-scan enabled:</strong> all endpoints matching configured HTTP methods are
 *       included, unless annotated with {@link SitemapExclude @SitemapExclude}.</li>
 *   <li><strong>Auto-scan disabled:</strong> only endpoints annotated with
 *       {@link Sitemap @Sitemap} are included.</li>
 *   <li>Endpoints with <strong>path variables</strong> (e.g. {@code /users/{id}}) are
 *       <strong>skipped</strong> with a warning log, since the actual URL values are unknown
 *       at scan time. Developers should add these URLs programmatically.</li>
 *   <li>A class-level {@code @Sitemap} applies to all eligible methods in that class.</li>
 *   <li>For each endpoint, locales are resolved via {@link SitemapLocaleResolver}.
 *       When locales are present, one {@link SitemapUrl} per locale is generated, each with
 *       the full alternates map for hreflang cross-referencing.</li>
 * </ul>
 *
 * <h3>Initialization</h3>
 * <ul>
 *   <li>{@link InitializationType#EAGER}: scan runs on {@code ApplicationReadyEvent}</li>
 *   <li>{@link InitializationType#LAZY}: scan runs on first {@code /sitemap.xml} request</li>
 * </ul>
 */
public class SitemapEndpointScanner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SitemapEndpointScanner.class);

    /** Pattern to detect path variables like {id} or {slug} in URL patterns. */
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{[^}]+}");

    private final RequestMappingHandlerMapping handlerMapping;
    private final SitemapHolder sitemapHolder;
    private final SitemapProperties properties;
    private final SitemapLocaleResolver localeResolver;

    /** Ensures the scan is only performed once. */
    private final AtomicBoolean scanned = new AtomicBoolean(false);

    /**
     * Constructs a new SitemapEndpointScanner.
     *
     * @param handlerMapping the Spring MVC handler mapping to scan
     * @param sitemapHolder  the holder to register discovered URLs in
     * @param properties     the sitemap configuration properties
     * @param localeResolver the locale resolver for hreflang generation
     */
    public SitemapEndpointScanner(
            RequestMappingHandlerMapping handlerMapping,
            SitemapHolder sitemapHolder,
            SitemapProperties properties,
            SitemapLocaleResolver localeResolver) {
        this.handlerMapping = handlerMapping;
        this.sitemapHolder = sitemapHolder;
        this.properties = properties;
        this.localeResolver = localeResolver;
    }

    /**
     * Handles the {@code ApplicationReadyEvent}. If initialization is set to
     * {@link InitializationType#EAGER}, triggers the endpoint scan immediately.
     *
     * @param event the application ready event
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties.getInitialization() == InitializationType.EAGER) {
            scan();
        }
    }

    /**
     * Scans all registered request mapping handler methods and registers matching
     * endpoints in the {@link SitemapHolder}.
     *
     * <p>This method is idempotent — it only runs once regardless of how many times
     * it is called (guarded by an {@link AtomicBoolean}).</p>
     */
    public void scan() {
        if (!scanned.compareAndSet(false, true)) {
            log.debug("Endpoint scan already performed, skipping");
            return;
        }

        log.info("Scanning controller endpoints for sitemap registration...");

        Set<String> allowedMethods = properties.getAutoScanMethods();
        List<SitemapUrl> discoveredUrls = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> !entry.getValue().getMethod().isAnnotationPresent(SitemapExclude.class))
                .flatMap(entry -> {
                    RequestMappingInfo mappingInfo = entry.getKey();
                    HandlerMethod handlerMethod = entry.getValue();
                    Sitemap effectiveAnnotation = resolveAnnotation(handlerMethod);

                    if (!shouldInclude(effectiveAnnotation, mappingInfo, allowedMethods)) {
                        return Stream.empty();
                    }

                    return extractPatterns(mappingInfo).stream()
                            .filter(pattern -> {
                                if (PATH_VARIABLE_PATTERN.matcher(pattern).find()) {
                                    log.warn("Skipping endpoint with path variables: {} — "
                                            + "add these URLs programmatically via SitemapHolder.add()", pattern);
                                    return false;
                                }
                                return true;
                            })
                            .flatMap(pattern -> buildUrlsForPattern(pattern, effectiveAnnotation).stream());
                })
                .toList();

        sitemapHolder.addAll(discoveredUrls);
        log.info("Sitemap endpoint scan complete: {} URLs registered", discoveredUrls.size());
    }

    /**
     * Resolves the effective {@link Sitemap} annotation for a handler method,
     * falling back from method-level to class-level.
     */
    private Sitemap resolveAnnotation(HandlerMethod handlerMethod) {
        return Optional.ofNullable(handlerMethod.getMethod().getAnnotation(Sitemap.class))
                .orElse(handlerMethod.getBeanType().getAnnotation(Sitemap.class));
    }

    /**
     * Builds one or more {@link SitemapUrl} entries for a given URL pattern.
     * When locales are resolved, one URL per locale is generated with the full alternates map.
     * Without locales, a single non-localized URL is produced.
     */
    private List<SitemapUrl> buildUrlsForPattern(String pattern, Sitemap annotation) {
        double priority = resolvePriority(annotation);
        ChangeFrequency changefreq = resolveChangefreq(annotation);
        LocalDateTime lastmod = resolveLastmod(annotation);
        String[] annotationLocales = annotation != null ? annotation.locales() : new String[0];

        List<String> locales = localeResolver.resolveLocales(annotationLocales);

        if (locales.isEmpty()) {
            return List.of(SitemapUrl.builder(localeResolver.buildUrl(pattern))
                    .priority(priority)
                    .changefreq(changefreq)
                    .lastmod(lastmod)
                    .build());
        }

        Map<String, String> alternates = localeResolver.buildAlternates(pattern, locales);
        return locales.stream()
                .map(locale -> SitemapUrl.builder(localeResolver.buildLocalizedUrl(pattern, locale))
                        .priority(priority)
                        .changefreq(changefreq)
                        .lastmod(lastmod)
                        .alternates(alternates)
                        .build())
                .toList();
    }

    /**
     * Determines whether an endpoint should be included in the sitemap based on
     * annotation presence, auto-scan setting, and HTTP method filtering.
     */
    private boolean shouldInclude(Sitemap annotation, RequestMappingInfo mappingInfo,
                                  Set<String> allowedMethods) {
        if (annotation == null && !properties.isAutoScan()) {
            return false;
        }

        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
        return methods.isEmpty() || methods.stream()
                .anyMatch(m -> allowedMethods.contains(m.name()));
    }

    /**
     * Extracts URL patterns from a {@link RequestMappingInfo}, preferring
     * Spring Boot 3.x PathPatternsCondition.
     */
    private Set<String> extractPatterns(RequestMappingInfo mappingInfo) {
        if (mappingInfo.getPathPatternsCondition() != null) {
            return mappingInfo.getPathPatternsCondition().getPatterns().stream()
                    .map(p -> p.getPatternString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<String> directPaths = mappingInfo.getDirectPaths();
        return (directPaths != null && !directPaths.isEmpty()) ? directPaths : Set.of();
    }

    /** Resolves priority from annotation, falling back to the global default. */
    private double resolvePriority(Sitemap annotation) {
        return (annotation != null && annotation.priority() >= 0)
                ? annotation.priority()
                : properties.getDefaultPriority();
    }

    /** Resolves change frequency from annotation, falling back to the global default. */
    private ChangeFrequency resolveChangefreq(Sitemap annotation) {
        return (annotation != null && annotation.changefreq() != ChangeFrequency.UNSET)
                ? annotation.changefreq()
                : properties.getDefaultChangefreq();
    }

    /**
     * Resolves the last modification date from the annotation string.
     * Supports {@code "2025-01-15"} (date only) and {@code "2025-01-15T10:30:00"} (date-time).
     *
     * @return the parsed LocalDateTime, or null if not set or parse fails
     */
    private LocalDateTime resolveLastmod(Sitemap annotation) {
        return Optional.ofNullable(annotation)
                .map(Sitemap::lastmod)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(value -> {
                    try {
                        String toParse = value.contains("T") ? value : value + "T00:00:00";
                        return LocalDateTime.parse(toParse, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (DateTimeParseException e) {
                        log.warn("Failed to parse lastmod value '{}': {}", value, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Returns whether the initial scan has been performed.
     *
     * @return true if scan() has been called at least once
     */
    public boolean isScanned() {
        return scanned.get();
    }
}
