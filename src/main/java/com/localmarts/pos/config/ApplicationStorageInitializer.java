package com.localmarts.pos.config;

import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ApplicationStorageInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String BASE_DIRECTORY_PROPERTY = "localmart.storage.base-dir";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String configuredBaseDirectory = applicationContext.getEnvironment()
                .getRequiredProperty(BASE_DIRECTORY_PROPERTY);
        Path baseDirectory = Path.of(configuredBaseDirectory).toAbsolutePath().normalize();

        try {
            Files.createDirectories(baseDirectory.resolve("database"));
            Files.createDirectories(baseDirectory.resolve("backups"));
            Files.createDirectories(baseDirectory.resolve("exports"));
            Files.createDirectories(baseDirectory.resolve("logs"));
        } catch (IOException exception) {
            throw new ApplicationContextException(
                    "Failed to create LocalMart POS storage directories at " + baseDirectory,
                    exception
            );
        }
    }
}
