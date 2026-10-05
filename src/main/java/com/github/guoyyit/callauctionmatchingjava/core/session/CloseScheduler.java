package com.github.guoyyit.callauctionmatchingjava.core.session;

import java.util.Random;

public class CloseScheduler {

    private final Random random;

    public CloseScheduler(long randomSeed) {
        this.random = new Random(randomSeed);
    }

    public long scheduleEndMillis(long windowStartMillis, long windowEndMillis) {
        if (windowEndMillis < windowStartMillis) {
            throw new IllegalArgumentException("windowEndMillis must not be before windowStartMillis");
        }
        long span = windowEndMillis - windowStartMillis;
        return windowStartMillis + (span == 0L ? 0L : Math.floorMod(random.nextLong(), span + 1L));
    }

    public Random getRandom() {
        return random;
    }
}
