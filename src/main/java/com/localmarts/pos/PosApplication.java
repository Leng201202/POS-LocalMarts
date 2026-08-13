package com.localmarts.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class PosApplication {

	public static void main(String[] args) throws IOException {
		createDirectory();
        SpringApplication.run(PosApplication.class, args);
        try {
            new ProcessBuilder("open", "https://localhost:8080").start();
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

    private static void createDirectory() throws IOException {
        try {
            Path basePath = Paths.get(System.getProperty("user.home"), "LocalMartPOS");
            Files.createDirectories(basePath.resolve("database"));
            Files.createDirectories(basePath.resolve("backups"));
            Files.createDirectories(basePath.resolve("exports"));
            Files.createDirectories(basePath.resolve("logs"));
            System.out.println("LocalMartPOS folders created at: " + basePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create LocalMartPOS folders", e);
        }
    }

}
