package com.bugcheck.agent;

import com.bugcheck.agent.config.AgentProperties;
import com.bugcheck.agent.config.OpenAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OpenAiProperties.class, AgentProperties.class})
public class BugcheckAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(BugcheckAgentApplication.class, args);
    }
}
