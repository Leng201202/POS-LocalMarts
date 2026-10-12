package com.localmarts.pos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoProfileConfigurationTest {

    @TempDir
    Path temporaryHome;

    @Test
    void demoProfileUsesLoopbackHttpAndIsolatedPersistentStorage() {
        try (ConfigurableApplicationContext context = runApplication("demo")) {
            Environment environment = context.getEnvironment();
            Path demoBase = temporaryHome.resolve("Library/Application Support/LocalMart POS Demo");

            assertThat(environment.acceptsProfiles(Profiles.of("demo"))).isTrue();
            assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(environment.getProperty("server.ssl.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:sqlite:" + demoBase.resolve("database/demo.db"));
            assertThat(environment.getProperty("localmart.browser.url"))
                    .isEqualTo("http://127.0.0.1:8080");

            assertStorageDirectoriesExist(demoBase);
            assertThat(temporaryHome.resolve("LocalMartPOS")).doesNotExist();
            assertThat(demoBase.resolve("database/demo.db")).doesNotExist();
        }
    }

    @Test
    void defaultConfigurationRetainsDevelopmentHttpsAndStoragePaths() {
        try (ConfigurableApplicationContext context = runApplication(null)) {
            Environment environment = context.getEnvironment();
            Path developmentBase = temporaryHome.resolve("LocalMartPOS");

            assertThat(environment.acceptsProfiles(Profiles.of("demo"))).isFalse();
            assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
            assertThat(environment.getProperty("server.ssl.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:sqlite:" + developmentBase.resolve("database/localmart-pos.db"));
            assertThat(environment.getProperty("server.ssl.key-store"))
                    .isEqualTo("file:" + developmentBase.resolve("certs/pos-https.p12"));
            assertThat(environment.getProperty("localmart.browser.url"))
                    .isEqualTo("https://localhost:8080");

            assertStorageDirectoriesExist(developmentBase);
            assertThat(temporaryHome.resolve("Library/Application Support/LocalMart POS Demo"))
                    .doesNotExist();
        }
    }

    private ConfigurableApplicationContext runApplication(String profile) {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(new ApplicationStorageInitializer());
        String mainResources = Path.of("src/main/resources").toAbsolutePath().normalize().toUri().toString();

        if (profile == null) {
            return application.run(
                    "--user.home=" + temporaryHome,
                    "--spring.config.location=" + mainResources,
                    "--spring.main.banner-mode=off"
            );
        }

        return application.run(
                "--user.home=" + temporaryHome,
                "--spring.profiles.active=" + profile,
                "--spring.config.location=" + mainResources,
                "--spring.main.banner-mode=off"
        );
    }

    private void assertStorageDirectoriesExist(Path baseDirectory) {
        assertThat(Files.isDirectory(baseDirectory.resolve("database"))).isTrue();
        assertThat(Files.isDirectory(baseDirectory.resolve("backups"))).isTrue();
        assertThat(Files.isDirectory(baseDirectory.resolve("exports"))).isTrue();
        assertThat(Files.isDirectory(baseDirectory.resolve("logs"))).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class TestApplication {
    }
}
