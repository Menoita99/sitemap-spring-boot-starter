plugins {
    java
    `java-library`
    `maven-publish`
    signing
    id("org.springframework.boot") version "3.4.3" apply false
    id("io.spring.dependency-management") version "1.1.7"
}

group = "net.menoita"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-autoconfigure")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("Sitemap Spring Boot Starter")
                description.set(
                    "A Spring Boot starter that generates sitemaps dynamically from annotated " +
                    "controller endpoints and programmatic additions, with full sitemap protocol " +
                    "compliance including hreflang and sitemap index support."
                )
                url.set("https://github.com/Menoita99/sitemap-spring-boot-starter")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("Menoita99")
                        name.set("Rui Menoita")
                        url.set("https://menoita.net")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Menoita99/sitemap-spring-boot-starter.git")
                    developerConnection.set("scm:git:ssh://github.com:Menoita99/sitemap-spring-boot-starter.git")
                    url.set("https://github.com/Menoita99/sitemap-spring-boot-starter")
                }
            }
        }
    }
    repositories {
        // Maven Central via Sonatype Central Portal (OSSRH Staging API compatibility service)
        maven {
            name = "CentralPortal"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("CENTRAL_TOKEN_USERNAME")
                password = System.getenv("CENTRAL_TOKEN_PASSWORD")
            }
        }
        // GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Menoita99/sitemap-spring-boot-starter")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

// Only require signing when actually publishing (not during local builds)
tasks.withType<Sign> {
    onlyIf { gradle.taskGraph.allTasks.any { it.name.startsWith("publish") } }
}
