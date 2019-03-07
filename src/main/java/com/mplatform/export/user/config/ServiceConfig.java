package com.mplatform.export.user.config;

import com.okta.sdk.authc.credentials.ClientCredentials;
import com.okta.sdk.authc.credentials.TokenClientCredentials;
import com.okta.sdk.client.Client;
import com.okta.sdk.client.Clients;
import liquibase.integration.spring.SpringLiquibase;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public @Data class ServiceConfig {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private Environment environment;

    @Bean
    Client client() {
        ClientCredentials clientCredentials = new TokenClientCredentials(applicationProperties.getToken());
        return Clients.builder().setOrgUrl(applicationProperties.getOrgUrl()).setClientCredentials(clientCredentials).build();
    }

/*    @Bean
    public SpringLiquibase liquibase() {
        SpringLiquibase liquibase = new SpringLiquibase();
        String url = environment.getProperty("spring.liquibase.url");
        String username = environment.getProperty("spring.liquibase.user");
        String password = environment.getProperty("spring.liquibase.password");
        String changeLog = environment.getProperty("spring.liquibase.change-log");

        liquibase.setChangeLog(changeLog);
        liquibase.setDataSource(DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build());
        return liquibase;
    }*/

    @Bean
    public String cronBean() {
        String cronValue = environment.getProperty("app.chron");
        return cronValue;
    }
}
