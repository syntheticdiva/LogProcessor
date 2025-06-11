package org.example;

import java.time.LocalDateTime;

public class LogEntry {
    LocalDateTime timestamp;
    String user;
    OperationType type;
    double amount;
    String relatedUser;

    public LogEntry() {}
}