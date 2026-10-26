package com.localmarts.pos;

import com.localmarts.pos.config.ApplicationStorageInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Locale;

@SpringBootApplication
@EnableScheduling
public class PosApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PosApplication.class);
        application.addInitializers(new ApplicationStorageInitializer());
        ConfigurableApplicationContext context = application.run(args);

        String browserUrl = context.getEnvironment().getRequiredProperty("localmart.browser.url");
        openBrowser(browserUrl);
    }

    private static void openBrowser(String browserUrl) {
        String operatingSystem = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        String[] command;
        if (operatingSystem.contains("mac")) {
            command = new String[]{"open", browserUrl};
        } else if (operatingSystem.contains("win")) {
            command = new String[]{"rundll32", "url.dll,FileProtocolHandler", browserUrl};
        } else {
            command = new String[]{"xdg-open", browserUrl};
        }

        try {
            new ProcessBuilder(command).start();
        } catch (Exception exception) {
            System.err.println("Open " + browserUrl + " in a browser to use LocalMart POS.");
        }
    }
}
