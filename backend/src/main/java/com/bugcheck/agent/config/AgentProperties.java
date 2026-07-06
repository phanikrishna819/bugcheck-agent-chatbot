package com.bugcheck.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(String assetsDir, int maxToolRounds) {
}
