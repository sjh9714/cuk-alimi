package com.cukalimi;

import com.cukalimi.config.AppProperties;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CukAlimiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CukAlimiApplication.class, args);
    }

    /** "오늘"은 서울 날짜다. 테스트는 이 빈을 바꿔 날짜를 고정한다. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
