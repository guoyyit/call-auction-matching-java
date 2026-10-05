package com.github.guoyyit.callauctionmatchingjava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.command.LoggedCommand;
import com.github.guoyyit.callauctionmatchingjava.core.log.CommandLog;
import com.github.guoyyit.callauctionmatchingjava.core.log.FileCommandLog;
import com.github.guoyyit.callauctionmatchingjava.core.engine.ImbalanceCalculator;
import com.github.guoyyit.callauctionmatchingjava.core.engine.PriceLimits;
import com.github.guoyyit.callauctionmatchingjava.core.engine.Ticks;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionView;
import com.github.guoyyit.callauctionmatchingjava.core.model.Imbalance;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import com.github.guoyyit.callauctionmatchingjava.core.session.AuctionKernel;
import com.github.guoyyit.callauctionmatchingjava.core.session.AuctionState;
import com.github.guoyyit.callauctionmatchingjava.core.session.CloseScheduler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class AuctionEngineService {

    private final AuctionProperties properties;
    private final CommandLog commandLog;
    private final AuctionState state = new AuctionState();
    private final AuctionKernel kernel = new AuctionKernel(state);
    private final CloseScheduler closeScheduler;
    private final LongSupplier clockMillis;
    private final Object lock = new Object();
    private ScheduledExecutorService scheduler;
    private boolean readOnly;

    public AuctionEngineService(AuctionProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, System::currentTimeMillis);
    }

    public AuctionEngineService(AuctionProperties properties, ObjectMapper objectMapper,
                                LongSupplier clockMillis) {
        this.properties = properties;
        this.commandLog = new FileCommandLog(Path.of(properties.getLogFile()), objectMapper);
        this.closeScheduler = new CloseScheduler(properties.getRandomSeed());
        this.clockMillis = clockMillis;
    }

    void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void runDueScheduledClose() {
        synchronized (lock) {
            if (state.getPhase() == Phase.COLLECTING
                    && state.getKind() == AuctionKind.CLOSE
                    && state.getScheduledEndTimestampMillis() != null
                    && clockMillis.getAsLong() >= state.getScheduledEndTimestampMillis()) {
                appendAndApply(new Command.FinishAuction(state.getScheduledEndTimestampMillis()));
            }
        }
    }

    @PostConstruct
    public void recover() throws IOException {
        synchronized (lock) {
            if (properties.isResetOnStartup()) {
                commandLog.reset();
            }
            List<LoggedCommand> logged = commandLog.readAll();
            for (LoggedCommand command : logged) {
                kernel.apply(command);
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "close-auction-scheduler");
                thread.setDaemon(true);
                return thread;
            });
            scheduleCloseIfNeeded();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public AuctionView startAuction(AuctionKind kind, BigDecimal referencePrice,
                                    UnfilledPolicy unfilledPolicy, Long fixedEndMillis) {
        synchronized (lock) {
            long now = clockMillis.getAsLong();
            if (state.getPhase() == Phase.COLLECTING) {
                throw new IllegalStateException("an auction is already collecting: " + state.getAuctionId());
            }
            BigDecimal effectiveReference = referencePrice;
            if (effectiveReference == null) {
                effectiveReference = state.getReferenceTick() == null ? null
                        : Ticks.toPrice(state.getReferenceTick(), properties.getTickSize());
            }
            if (effectiveReference == null) {
                throw new IllegalArgumentException("referencePrice is required for the first auction");
            }
            long referenceTick = Ticks.toTickIndex(effectiveReference, properties.getTickSize());
            PriceLimits limits = PriceLimits.from(effectiveReference, properties.getTickSize(),
                    properties.getLimitChangePercent());
            long endMillis;
            if (fixedEndMillis != null) {
                endMillis = fixedEndMillis;
            } else if (kind == AuctionKind.CLOSE) {
                long windowStart = now + properties.getCloseWindowStartMillisAfterStart();
                long windowEnd = windowStart + properties.getCloseWindowDurationMillis();
                endMillis = closeScheduler.scheduleEndMillis(windowStart, windowEnd);
            } else {
                endMillis = Long.MAX_VALUE;
            }
            UnfilledPolicy policy = unfilledPolicy != null ? unfilledPolicy
                    : properties.getDefaultUnfilledPolicy();
            String auctionId = "AUC-" + (state.getAuctionCount() + 1L);
            Command.StartAuction start = new Command.StartAuction(
                    auctionId, kind, now, properties.getTickSize(), effectiveReference,
                    Ticks.toPrice(limits.lowerTick(), properties.getTickSize()),
                    Ticks.toPrice(limits.upperTick(), properties.getTickSize()),
                    policy, endMillis);
            state.setAuctionCount(state.getAuctionCount() + 1L);
            appendAndApply(start);
            scheduleCloseIfNeeded();
            return view();
        }
    }

    public AuctionResult finishAuction() {
        synchronized (lock) {
            if (state.getPhase() != Phase.COLLECTING) {
                throw new IllegalStateException("no auction is collecting");
            }
            appendAndApply(new Command.FinishAuction(clockMillis.getAsLong()));
            return state.lastResult();
        }
    }

    public Order placeOrder(String clientOrderId, Command.PlaceOrder command) {
        synchronized (lock) {
            requireCollecting();
            appendAndApply(command);
            return state.getBook().get(clientOrderId);
        }
    }

    public Order cancelOrder(String clientOrderId, String origClientOrderId) {
        synchronized (lock) {
            requireCollecting();
            Order before = state.getBook().get(origClientOrderId);
            if (before == null) {
                throw new IllegalArgumentException("active order not found: " + origClientOrderId);
            }
            appendAndApply(new Command.CancelOrder(clientOrderId, origClientOrderId));
            return before;
        }
    }

    public Order replaceOrder(String clientOrderId, String origClientOrderId,
                              Command.ReplaceOrder command) {
        synchronized (lock) {
            requireCollecting();
            if (state.getBook().get(origClientOrderId) == null) {
                throw new IllegalArgumentException("active order not found: " + origClientOrderId);
            }
            appendAndApply(command);
            return state.getBook().get(clientOrderId);
        }
    }

    public Imbalance imbalance() {
        synchronized (lock) {
            if (state.getPhase() != Phase.COLLECTING) {
                throw new IllegalStateException("no auction is collecting");
            }
            return ImbalanceCalculator.calculate(state.getBook().activeOrders(),
                    state.getReferenceTick(),
                    properties.getImbalance().getLevel(),
                    properties.getImbalance().getQuantityBucketSize(),
                    properties.getImbalance().isDiscloseReferencePrice());
        }
    }

    public AuctionState snapshotState() {
        synchronized (lock) {
            return state;
        }
    }

    public AuctionView view() {
        synchronized (lock) {
            return new AuctionView(
                    state.getAuctionId(),
                    state.getKind(),
                    state.getPhase(),
                    state.getTickSize(),
                    state.getReferenceTick() == null ? 0L : state.getReferenceTick(),
                    state.getLowerLimitTick() == null ? 0L : state.getLowerLimitTick(),
                    state.getUpperLimitTick() == null ? 0L : state.getUpperLimitTick(),
                    state.getUnfilledPolicy(),
                    state.getScheduledEndTimestampMillis(),
                    state.getBook().activeOrders().size());
        }
    }

    public Collection<Order> activeOrders() {
        synchronized (lock) {
            return state.getBook().activeOrders();
        }
    }

    public List<AuctionResult> results() {
        synchronized (lock) {
            return List.copyOf(state.getResults());
        }
    }

    public long appendAndApply(Command command) {
        if (readOnly) {
            throw new IllegalStateException("service is in read-only recovery mode");
        }
        long seq = state.getLastCommandSeq() + 1L;
        LoggedCommand logged = new LoggedCommand(seq, clockMillis.getAsLong(), command);
        try {
            commandLog.append(logged);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to persist command", exception);
        }
        kernel.apply(logged);
        return seq;
    }

    private void requireCollecting() {
        if (state.getPhase() != Phase.COLLECTING) {
            throw new IllegalStateException("no auction is collecting");
        }
    }

    private void scheduleCloseIfNeeded() {
        if (scheduler == null || state.getPhase() != Phase.COLLECTING
                || state.getKind() != AuctionKind.CLOSE
                || state.getScheduledEndTimestampMillis() == null
                || state.getScheduledEndTimestampMillis() == Long.MAX_VALUE) {
            return;
        }
        long delay = Math.max(0L, state.getScheduledEndTimestampMillis() - clockMillis.getAsLong());
        scheduler.schedule(() -> {
            synchronized (lock) {
                if (state.getPhase() == Phase.COLLECTING
                        && state.getKind() == AuctionKind.CLOSE) {
                    appendAndApply(new Command.FinishAuction(clockMillis.getAsLong()));
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
