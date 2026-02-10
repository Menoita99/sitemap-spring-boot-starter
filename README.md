# Sitemap Spring Boot Starter

A Spring Boot library that generates sitemaps dynamically from annotated controller endpoints and programmatic additions, served as XML at `/sitemap.xml` with full [sitemaps.org protocol](https://www.sitemaps.org/protocol.html) compliance.

## Features

- **Annotation-driven**: Mark endpoints with `@Sitemap(priority=0.8, changefreq=WEEKLY)` to include them in the sitemap
- **Auto-scan**: Optionally scan all `@GetMapping` endpoints automatically
- **Programmatic API**: Add/remove URLs at runtime via `SitemapHolder`
- **Multilingual (hreflang)**: Generate `<xhtml:link rel="alternate" hreflang="...">` entries with configurable locale resolution (annotation > config > auto-detect)
- **Two locale URL patterns**: `PATH_PREFIX` (`/en/about`) or `QUERY_PARAM` (`?lang=en`)
- **Sitemap Index**: Automatically splits into multiple sitemaps when URLs exceed 50,000
- **Thread-safe**: Volatile-cached XML with `ConcurrentHashMap` and `ReentrantReadWriteLock`
- **Eager or Lazy init**: Scan at startup or on first `/sitemap.xml` request

## Quick Start

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("net.menoita:sitemap-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

### 2. Configure base URL

```yaml
# application.yml
sitemap:
  base-url: https://www.example.com
```

### 3. Annotate your endpoints

```java
@RestController
public class PageController {

    @Sitemap(priority = 1.0, changefreq = ChangeFrequency.DAILY)
    @GetMapping("/")
    public String home() { return "home"; }

    @Sitemap(priority = 0.8, changefreq = ChangeFrequency.WEEKLY)
    @GetMapping("/about")
    public String about() { return "about"; }

    @GetMapping("/internal-api")  // not in sitemap
    public String internalApi() { return "data"; }
}
```

Visit `http://localhost:8080/sitemap.xml` and the generated XML is served automatically.

## Configuration Reference

All properties are prefixed with `sitemap.`:

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Enable/disable the entire library |
| `base-url` | `String` | *required* | Base URL of the site (e.g. `https://www.example.com`) |
| `auto-scan` | `boolean` | `false` | Auto-include all controller endpoints matching `auto-scan-methods` |
| `auto-scan-methods` | `Set<String>` | `GET` | HTTP methods to include during auto-scan |
| `default-priority` | `double` | `0.5` | Default priority for URLs without explicit priority |
| `default-changefreq` | `ChangeFrequency` | `UNSET` | Default change frequency (omitted from XML when UNSET) |
| `initialization` | `InitializationType` | `EAGER` | `EAGER` (scan at startup) or `LAZY` (scan on first request) |
| `max-urls-per-sitemap` | `int` | `50000` | Max URLs per file before sitemap index mode kicks in |
| `locales` | `List<String>` | *empty* | Locale codes for hreflang generation (e.g. `en, pt, fr`) |
| `locale-url-pattern` | `LocaleUrlPattern` | `PATH_PREFIX` | `PATH_PREFIX` or `QUERY_PARAM` |
| `locale-query-param-name` | `String` | `lang` | Query param name when using `QUERY_PARAM` pattern |
| `default-locale` | `String` | *null* | The default locale for `x-default` hreflang |
| `omit-default-locale-in-url` | `boolean` | `false` | Omit locale identifier for the default locale in URLs |

## Usage Examples

### Auto-scan with exclusions

```yaml
sitemap:
  base-url: https://www.example.com
  auto-scan: true
```

```java
@RestController
public class BlogController {

    @GetMapping("/blog")        // auto-included
    public String blogList() { ... }

    @SitemapExclude
    @GetMapping("/blog/draft")  // excluded
    public String draft() { ... }
}
```

### Per-endpoint locale override

```java
@RestController
public class ProductController {

    // Annotation locales override config and auto-detection
    @Sitemap(priority = 0.9, locales = {"en", "pt", "es"})
    @GetMapping("/products")
    public String products() { ... }

    // Falls back to sitemap.locales config
    @Sitemap(priority = 0.6)
    @GetMapping("/faq")
    public String faq() { ... }
}
```

### Programmatic dynamic addition

```java
@Component
public class BlogSitemapPopulator implements CommandLineRunner {

    private final SitemapHolder sitemapHolder;
    private final BlogRepository blogRepository;

    @Override
    public void run(String... args) {
        blogRepository.findAllSlugs().forEach(slug ->
            sitemapHolder.add(
                SitemapUrl.builder("https://www.example.com/blog/" + slug)
                    .priority(0.7)
                    .changefreq(ChangeFrequency.MONTHLY)
                    .lastmod(LocalDateTime.now())
                    .build()
            )
        );
    }
}
```

### Multilingual with PATH_PREFIX

```yaml
sitemap:
  base-url: https://www.example.com
  locales: en, pt, es
  locale-url-pattern: path_prefix
  default-locale: en
  omit-default-locale-in-url: true
```

Generated XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:xhtml="http://www.w3.org/1999/xhtml">
  <url>
    <loc>https://www.example.com/about</loc>
    <xhtml:link rel="alternate" hreflang="en" href="https://www.example.com/about"/>
    <xhtml:link rel="alternate" hreflang="pt" href="https://www.example.com/pt/about"/>
    <xhtml:link rel="alternate" hreflang="es" href="https://www.example.com/es/about"/>
    <xhtml:link rel="alternate" hreflang="x-default" href="https://www.example.com/about"/>
  </url>
  <url>
    <loc>https://www.example.com/pt/about</loc>
    <xhtml:link rel="alternate" hreflang="en" href="https://www.example.com/about"/>
    <xhtml:link rel="alternate" hreflang="pt" href="https://www.example.com/pt/about"/>
    <xhtml:link rel="alternate" hreflang="es" href="https://www.example.com/es/about"/>
    <xhtml:link rel="alternate" hreflang="x-default" href="https://www.example.com/about"/>
  </url>
  <!-- ... es locale entry ... -->
</urlset>
```

### Multilingual with QUERY_PARAM (e.g. dommy.io)

```yaml
sitemap:
  base-url: https://dommy.io
  locales: en, pt, es, fr
  locale-url-pattern: query_param
  locale-query-param-name: lang
  default-locale: en
  omit-default-locale-in-url: false
```

Generated XML:

```xml
<url>
  <loc>https://dommy.io/?lang=en</loc>
  <xhtml:link rel="alternate" hreflang="en" href="https://dommy.io/?lang=en"/>
  <xhtml:link rel="alternate" hreflang="pt" href="https://dommy.io/?lang=pt"/>
  <xhtml:link rel="alternate" hreflang="fr" href="https://dommy.io/?lang=fr"/>
  <xhtml:link rel="alternate" hreflang="x-default" href="https://dommy.io/?lang=en"/>
</url>
```

## Annotations

### `@Sitemap`

Place on controller methods or classes. When on a class, all eligible endpoints are included.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `priority` | `double` | `-1` (use global) | URL priority (0.0 to 1.0) |
| `changefreq` | `ChangeFrequency` | `UNSET` | Change frequency hint |
| `lastmod` | `String` | `""` | Last modified date (`"2025-01-15"` or `"2025-01-15T10:30:00"`) |
| `locales` | `String[]` | `{}` | Override locale list for this endpoint |

### `@SitemapExclude`

Place on controller methods to exclude them from the sitemap, even when auto-scan is enabled or a class-level `@Sitemap` is present.

### `@EnableSitemap`

Optional. Place on your `@SpringBootApplication` class to explicitly import the auto-configuration. Not needed if Spring Boot's auto-configuration mechanism is active.

## Path Variables

Endpoints with path variables (e.g. `/users/{id}`) are automatically skipped during scanning since the actual URL values are unknown at scan time. A `WARN`-level log message is emitted. Add these URLs programmatically:

```java
userRepository.findAll().forEach(user ->
    sitemapHolder.add(
        SitemapUrl.builder("https://example.com/users/" + user.getId())
            .priority(0.6)
            .build()
    )
);
```

## Sitemap Index

When URLs exceed `max-urls-per-sitemap` (default: 50,000), the library automatically serves a sitemap index at `/sitemap.xml` and individual sitemaps at `/sitemap-1.xml`, `/sitemap-2.xml`, etc.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <sitemap>
    <loc>https://www.example.com/sitemap-1.xml</loc>
  </sitemap>
  <sitemap>
    <loc>https://www.example.com/sitemap-2.xml</loc>
  </sitemap>
</sitemapindex>
```

## Thread Safety

`SitemapHolder` is fully thread-safe:

- **Storage**: `ConcurrentHashMap` for lock-free concurrent reads/writes
- **Cache**: `volatile` fields ensure cross-thread visibility when XML is invalidated
- **Generation**: `ReentrantReadWriteLock` prevents thundering herd on cache regeneration

## Requirements

- Java 17+
- Spring Boot 3.x / 4.x

## License

MIT
