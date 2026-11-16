package com.cukalimi.notice;

import com.cukalimi.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LocatorConfig {

    @Bean
    DeadlineLocators deadlineLocators(AppProperties props) {
        String key = props.transcribe() == null ? null : props.transcribe().apiKey();
        AppProperties.Locator l = props.locator();
        return new DeadlineLocators(new ClaudeDeadlineLocator(key, l == null ? null : l.model()),
                new ClaudeDeadlineLocator(key, l == null ? null : l.adjudicatorModel()));
    }
}
