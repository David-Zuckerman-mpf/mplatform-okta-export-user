package com.mplatform.export.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "okta")
public @Data class ApplicationProperties {

    private String orgUrl;
    private String token;
    private Map<String, String> appIds;

    public String getAppId(String appName){
        return appIds.get(appName);
    }

}
