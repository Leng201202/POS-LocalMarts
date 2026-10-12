package com.localmarts.pos;

import com.localmarts.pos.config.ApplicationStorageInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PosApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PosApplication.class);
        application.addInitializers(new ApplicationStorageInitializer());
        ConfigurableApplicationContext context = application.run(args);

        try {
            String browserUrl = context.getEnvironment().getRequiredProperty("localmart.browser.url");
            new ProcessBuilder("open", browserUrl).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
