package pt.eventlab.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.eventlab.messaging.MessageSupportConfiguration;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(MessageSupportConfiguration.class)
@EnableScheduling
public class LabConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabConsoleApplication.class, args);
    }
}
