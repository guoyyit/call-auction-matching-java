package com.github.guoyyit.callauctionmatchingjava.core.log;

import com.github.guoyyit.callauctionmatchingjava.core.command.LoggedCommand;

import java.io.IOException;
import java.util.List;

public interface CommandLog {

    void append(LoggedCommand command) throws IOException;

    List<LoggedCommand> readAll() throws IOException;

    void reset() throws IOException;
}
