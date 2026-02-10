package net.menoita.sitemap.annotation;

import net.menoita.sitemap.config.SitemapAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables sitemap generation and auto-configuration.
 *
 * <p>Place on your main {@code @SpringBootApplication} class to explicitly enable
 * the sitemap functionality. This is an <strong>optional</strong> alternative to
 * relying on Spring Boot's auto-configuration mechanism via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * @EnableSitemap
 * @SpringBootApplication
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(SitemapAutoConfiguration.class)
public @interface EnableSitemap {
}
