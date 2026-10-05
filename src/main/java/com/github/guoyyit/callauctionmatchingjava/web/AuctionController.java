package com.github.guoyyit.callauctionmatchingjava.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.guoyyit.callauctionmatchingjava.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.model.ImbalanceSnapshot;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Trade;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionService;
import com.github.guoyyit.callauctionmatchingjava.web.dto.ApiResponse;
import com.github.guoyyit.callauctionmatchingjava.web.dto.OrderRequest;
import com.github.guoyyit.callauctionmatchingjava.web.dto.OrderView;
import com.github.guoyyit.callauctionmatchingjava.web.dto.ReplaceRequest;

@RestController
@RequestMapping("/api/auction")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping("/orders")
    public ApiResponse<OrderView> placeOrder(@RequestBody OrderRequest request) {
        Order order = auctionService.placeOrder(request);
        return ApiResponse.ok(toView(order));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<OrderView> cancelOrder(@PathVariable long orderId) {
        Order order = auctionService.cancelOrder(orderId);
        return ApiResponse.ok(toView(order));
    }

    @PostMapping("/orders/replace")
    public ApiResponse<OrderView> replaceOrder(@RequestBody ReplaceRequest request) {
        Order order = auctionService.replaceOrder(request);
        return ApiResponse.ok(toView(order));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderView>> listOrders() {
        return ApiResponse.ok(auctionService.getOrders().stream().map(this::toView).toList());
    }

    @GetMapping("/trades")
    public ApiResponse<List<Trade>> listTrades() {
        return ApiResponse.ok(auctionService.getTrades());
    }

    @GetMapping("/results")
    public ApiResponse<List<AuctionResult>> listResults() {
        return ApiResponse.ok(auctionService.getAuctionResults());
    }

    @GetMapping("/phase")
    public ApiResponse<Map<String, Object>> phase() {
        return ApiResponse.ok(Map.of("phase", auctionService.getPhase().name()));
    }

    @GetMapping("/imbalance")
    public ApiResponse<ImbalanceSnapshot> imbalance() {
        return ApiResponse.ok(auctionService.getImbalance());
    }

    @PostMapping("/phase/open-match")
    public ApiResponse<AuctionResult> matchOpenAuction() {
        return ApiResponse.ok(auctionService.endCurrentAuction());
    }

    @PostMapping("/phase/start-close")
    public ApiResponse<Map<String, Object>> startClose() {
        Phase phase = auctionService.startCloseAuction();
        return ApiResponse.ok(Map.of("phase", phase.name()));
    }

    @PostMapping("/phase/close-match")
    public ApiResponse<AuctionResult> matchCloseAuction() {
        return ApiResponse.ok(auctionService.endCurrentAuction());
    }

    @PostMapping("/phase/close-poll")
    public ApiResponse<AuctionResult> pollCloseExpiry() {
        return ApiResponse.ok(auctionService.maybeExpireCloseAuction());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(ex.getMessage()));
    }

    private OrderView toView(Order order) {
        return OrderView.from(order,
                ticks -> auctionService.getNormalizer().toPriceDouble(ticks));
    }
}
