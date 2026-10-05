package com.github.guoyyit.callauctionmatchingjava.tests;

import java.nio.file.Path;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.engine.PriceNormalizer;
import com.github.guoyyit.callauctionmatchingjava.journal.EventJournal;
import com.github.guoyyit.callauctionmatchingjava.model.UnmatchedPolicy;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionService;
import com.github.guoyyit.callauctionmatchingjava.service.PhaseScheduler;

final class TestSupport {

    private TestSupport() {
    }

    static AuctionProperties properties(Path journalDir, UnmatchedPolicy policy, long seed) {
        AuctionProperties properties = new AuctionProperties();
        properties.setSymbol("600000");
        properties.setTickSize(0.01);
        properties.setLimitUpRatio(0.10);
        properties.setLimitDownRatio(0.10);
        properties.setReferencePrice(10.00);
        properties.setUnmatchedPolicy(policy);
        properties.setRandomSeed(seed);
        properties.setJournalDir(journalDir.toString());
        properties.setRecoveryEnabled(false);
        properties.setCloseWindowStartMillis(2_000L);
        properties.setCloseWindowLengthMillis(1_000L);
        return properties;
    }

    static AuctionService service(AuctionProperties properties) {
        return service(properties, new MutableClock(0L));
    }

    static AuctionService service(AuctionProperties properties, MutableClock clock) {
        PriceNormalizer normalizer = new PriceNormalizer(properties.getTickSize());
        EventJournal journal = new EventJournal(properties.getJournalDir());
        PhaseScheduler scheduler = new PhaseScheduler(properties, clock::get);
        return new AuctionService(properties, normalizer, journal, scheduler);
    }

    static AuctionService recover(AuctionProperties properties, MutableClock clock) {
        PriceNormalizer normalizer = new PriceNormalizer(properties.getTickSize());
        EventJournal journal = new EventJournal(properties.getJournalDir());
        PhaseScheduler scheduler = new PhaseScheduler(properties, clock::get);
        AuctionService service = new AuctionService(properties, normalizer, journal, scheduler);
        service.recover();
        return service;
    }

    static class MutableClock {
        private long value;

        MutableClock(long value) {
            this.value = value;
        }

        long get() {
            return value;
        }

        void set(long value) {
            this.value = value;
        }

        void advance(long delta) {
            this.value += delta;
        }
    }
}
