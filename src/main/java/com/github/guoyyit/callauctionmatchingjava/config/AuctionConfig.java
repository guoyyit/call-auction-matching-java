package com.github.guoyyit.callauctionmatchingjava.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionEngineService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuctionProperties.class)
public class AuctionConfig {

    @Bean
    public AuctionEngineService auctionEngineService(AuctionProperties properties, ObjectMapper objectMapper) {
        return new AuctionEngineService(properties, objectMapper);
    }
}
