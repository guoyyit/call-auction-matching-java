package com.github.guoyyit.callauctionmatchingjava.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.callauctionmatchingjava.core.command.LoggedCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileCommandLog implements CommandLog {

    private final Path file;
    private final ObjectMapper objectMapper;

    public FileCommandLog(Path file, ObjectMapper objectMapper) {
        this.file = file;
        this.objectMapper = objectMapper;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot initialize command log: " + file, exception);
        }
    }

    @Override
    public synchronized void append(LoggedCommand command) throws IOException {
        String line = objectMapper.writeValueAsString(command);
        Files.writeString(file, line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.DSYNC);
    }

    @Override
    public synchronized List<LoggedCommand> readAll() throws IOException {
        List<LoggedCommand> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                result.add(objectMapper.readValue(line, LoggedCommand.class));
            }
        }
        return result;
    }

    @Override
    public synchronized void reset() throws IOException {
        Files.writeString(file, "", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);
    }
}
