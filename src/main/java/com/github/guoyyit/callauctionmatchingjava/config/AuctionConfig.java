package com.github.guoyyit.callauctionmatchingjava.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.github.guoyyit.callauctionmatchingjava.engine.PriceNormalizer;
import com.github.guoyyit.callauctionmatchingjava.journal.EventJournal;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionService;
import com.github.guoyyit.callauctionmatchingjava.service.PhaseScheduler;

@Configuration
@EnableConfigurationProperties(AuctionProperties.class)
public class AuctionConfig {

    @org.springframework.context.annotation.Bean
    public PriceNormalizer priceNormalizer(AuctionProperties properties) {
        return new PriceNormalizer(properties.getTickSize());
    }

    @org.springframework.context.annotation.Bean
    public EventJournal eventJournal(AuctionProperties properties) {
        return new EventJournal(properties.getJournalDir());
    }

    @org.springframework.context.annotation.Bean
    public PhaseScheduler phaseScheduler(AuctionProperties properties) {
        return new PhaseScheduler(properties, System::currentTimeMillis);
    }

    @org.springframework.context.annotation.Bean
    public AuctionService auctionService(AuctionProperties properties,
                                         PriceNormalizer normalizer,
                                         EventJournal eventJournal,
                                         PhaseScheduler phaseScheduler) {
        AuctionService service = new AuctionService(
                properties, normalizer, eventJournal, phaseScheduler);
        if (properties.isRecoveryEnabled()) {
            service.recover();
        }
        return service;
    }
}
