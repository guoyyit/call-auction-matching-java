package com.github.guoyyit.callauctionmatchingjava.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期性检查收盘竞价是否到达其随机结束时刻，到达即在服务锁内完成撮合。
 */
@Component
public class CloseAuctionMonitor {

    private final AuctionService auctionService;

    public CloseAuctionMonitor(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedDelayString = "${call-auction.close-poll-millis:100}")
    public void poll() {
        auctionService.maybeExpireCloseAuction();
    }
}
