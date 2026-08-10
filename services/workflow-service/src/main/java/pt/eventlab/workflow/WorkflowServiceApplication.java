package pt.eventlab.workflow;

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
public class WorkflowServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowServiceApplication.class, args);
    }
}
