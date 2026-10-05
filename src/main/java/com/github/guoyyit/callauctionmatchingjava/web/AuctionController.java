package com.github.guoyyit.callauctionmatchingjava.web;

import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionView;
import com.github.guoyyit.callauctionmatchingjava.core.model.Imbalance;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionEngineService;
import com.github.guoyyit.callauctionmatchingjava.web.Dtos.CancelRequest;
import com.github.guoyyit.callauctionmatchingjava.web.Dtos.OrderRequest;
import com.github.guoyyit.callauctionmatchingjava.web.Dtos.ReplaceRequest;
import com.github.guoyyit.callauctionmatchingjava.web.Dtos.StartRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/auction")
public class AuctionController {

    private final AuctionEngineService service;

    public AuctionController(AuctionEngineService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public AuctionView start(@RequestBody StartRequest request) {
        if (request.kind() == null) {
            throw new AuctionException("kind is required (OPEN or CLOSE)");
        }
        return service.startAuction(request.kind(), request.referencePrice(),
                request.unfilledPolicy(), request.fixedEndMillis());
    }

    @PostMapping("/finish")
    public AuctionResult finish() {
        return service.finishAuction();
    }

    @PostMapping("/orders")
    public Order place(@RequestBody OrderRequest request) {
        validateOrderRequest(request);
        Command.PlaceOrder command = new Command.PlaceOrder(
                request.clientOrderId(), request.symbol(), request.side(),
                request.limitPrice(), request.market(), request.quantity());
        return service.placeOrder(request.clientOrderId(), command);
    }

    @PostMapping("/orders/cancel")
    public Order cancel(@RequestBody CancelRequest request) {
        requireText(request.clientOrderId(), "clientOrderId");
        String cancelId = "CXL-" + request.clientOrderId();
        return service.cancelOrder(cancelId, request.clientOrderId());
    }

    @PostMapping("/orders/{origClientOrderId}/replace")
    public Order replace(@PathVariable String origClientOrderId, @RequestBody ReplaceRequest request) {
        requireText(request.clientOrderId(), "clientOrderId");
        if (request.quantity() <= 0L) {
            throw new AuctionException("quantity must be positive");
        }
        if (!request.market() && request.limitPrice() == null) {
            throw new AuctionException("limitPrice is required for non-market orders");
        }
        Command.ReplaceOrder command = new Command.ReplaceOrder(
                request.clientOrderId(), origClientOrderId,
                request.limitPrice(), request.market(), request.quantity());
        return service.replaceOrder(request.clientOrderId(), origClientOrderId, command);
    }

    @GetMapping("/imbalance")
    public Imbalance imbalance() {
        return service.imbalance();
    }

    @GetMapping("/orders")
    public Collection<Order> orders() {
        return service.activeOrders();
    }

    @GetMapping("/results")
    public List<AuctionResult> results() {
        return service.results();
    }

    private void validateOrderRequest(OrderRequest request) {
        requireText(request.clientOrderId(), "clientOrderId");
        requireText(request.symbol(), "symbol");
        if (request.side() == null) {
            throw new AuctionException("side is required");
        }
        if (request.quantity() <= 0L) {
            throw new AuctionException("quantity must be positive");
        }
        if (!request.market() && request.limitPrice() == null) {
            throw new AuctionException("limitPrice is required for non-market orders");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AuctionException(field + " is required");
        }
    }
}
