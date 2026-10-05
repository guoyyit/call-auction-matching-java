package com.github.guoyyit.callauctionmatchingjava.core.command;

public record LoggedCommand(
        long seq,
        long timestampMillis,
        Command command
) {
}
