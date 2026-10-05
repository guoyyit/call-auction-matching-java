package com.github.guoyyit.callauctionmatchingjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionEngineService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class TestSupport {

    private TestSupport() {
    }

    public static AuctionProperties properties(Path logFile, long seed) {
        AuctionProperties properties = new AuctionProperties();
        properties.setSymbol("SEC");
        properties.setLogFile(logFile.toString());
        properties.setRandomSeed(seed);
        return properties;
    }

    public static Context newContext(Path logFile, long seed) {
        AuctionProperties properties = properties(logFile, seed);
        AtomicLong clock = new AtomicLong(1_000_000L);
        AuctionEngineService service = new AuctionEngineService(properties, new ObjectMapper(), clock::get);
        return new Context(service, properties, clock);
    }

    public record Context(AuctionEngineService service, AuctionProperties properties, AtomicLong clock) {

        public void recover() throws IOException {
            service.recover();
        }

        public AuctionEngineService newRecoveredService() throws IOException {
            AuctionEngineService recovered = new AuctionEngineService(properties, new ObjectMapper(), clock::get);
            recovered.recover();
            recovered.setReadOnly(true);
            return recovered;
        }
    }
}
