package com.github.guoyyit.callauctionmatchingjava.journal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 追加式写前日志(JSON Lines)。
 *
 * <p>每条事件以一行 JSON 落盘并 flush 输出流；调用方可在关键边界
 * (状态变更成功后)依赖其持久化。恢复时 {@link #readAll()} 按文件顺序
 * 返回全部完整行，半截的最后一行(崩溃造成)被忽略而不影响前缀一致性。</p>
 */
public class EventJournal implements AutoCloseable {

    private final Path logFile;
    private final ObjectMapper objectMapper;
    private final java.io.BufferedWriter writer;
    private long lastSeq;

    public EventJournal(String directory) {
        this(Path.of(directory), 0L);
    }

    public EventJournal(Path directory, long startSeq) {
        try {
            Files.createDirectories(directory);
            this.logFile = directory.resolve("events.log");
            this.objectMapper = new ObjectMapper();
            if (Files.notExists(logFile)) {
                Files.createFile(logFile);
            }
            this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
            this.lastSeq = startSeq;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open journal in " + directory, e);
        }
    }

    /** 分配下一个全局单调递增的事件序号。 */
    public synchronized long nextSeq() {
        return ++lastSeq;
    }

    public synchronized long lastSeq() {
        return lastSeq;
    }

    /** 追加事件并立即 flush；返回该事件。 */
    public synchronized JournalEvent append(JournalEvent event) {
        try {
            writer.write(objectMapper.writeValueAsString(event));
            writer.newLine();
            writer.flush();
            lastSeq = event.seq();
            return event;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append journal event", e);
        }
    }

    /** 读取全部完整事件(按落盘顺序)。 */
    public List<JournalEvent> readAll() {
        List<JournalEvent> events = new ArrayList<>();
        try (Stream<String> lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    return;
                }
                try {
                    events.add(objectMapper.readValue(trimmed, JournalEvent.class));
                } catch (IOException parseFailure) {
                    throw new UncheckedIOException("corrupt journal line: " + trimmed, parseFailure);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read journal", e);
        }
        return events;
    }

    public Path getLogFile() {
        return logFile;
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to close journal", e);
        }
    }
}
