package pt.eventlab.fulfilment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.eventlab.messaging.MessageSupportConfiguration;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Import(MessageSupportConfiguration.class)
public class FulfilmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfilmentServiceApplication.class, args);
    }
}
