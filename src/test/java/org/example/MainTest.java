package org.example;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @Test
    void processLine_TransferredOperation_CreatesEntriesForBothUsers() {
        Map<String, List<LogEntry>> userEntries = new HashMap<>();
        String line = "[2025-05-10 10:03:23] user002 transferred 990.00 to user001";

        Main.processLine(line, userEntries);

        List<LogEntry> senderEntries = userEntries.get("user002");
        assertThat(senderEntries).hasSize(1);
        LogEntry senderEntry = senderEntries.get(0);
        assertThat(senderEntry.type).isEqualTo(OperationType.TRANSFERRED);
        assertThat(senderEntry.amount).isEqualTo(990.00);
        assertThat(senderEntry.relatedUser).isEqualTo("user001");

        List<LogEntry> receiverEntries = userEntries.get("user001");
        assertThat(receiverEntries).hasSize(1);
        LogEntry receiverEntry = receiverEntries.get(0);
        assertThat(receiverEntry.type).isEqualTo(OperationType.RECIVED);
        assertThat(receiverEntry.amount).isEqualTo(990.00);
        assertThat(receiverEntry.relatedUser).isEqualTo("user002");
    }

    @Test
    void processLine_BalanceInquiry_ParsesCorrectly() {
        Map<String, List<LogEntry>> userEntries = new HashMap<>();
        String line = "[2025-05-10 09:00:22] user001 balance inquiry 1000.00";

        Main.processLine(line, userEntries);

        LogEntry entry = userEntries.get("user001").get(0);
        assertThat(entry.type).isEqualTo(OperationType.BALANCE_INQUIRY);
        assertThat(entry.amount).isEqualTo(1000.00);
        assertThat(entry.relatedUser).isNull();
    }

    @Test
    void processLine_InvalidLine_IgnoresEntry() {
        Map<String, List<LogEntry>> userEntries = new HashMap<>();
        String line = "Invalid log line";

        Main.processLine(line, userEntries);

        assertThat(userEntries).isEmpty();
    }
    @Test
    void formatEntry_Transferred_FormatsCorrectly() {
        LogEntry entry = new LogEntry();
        entry.timestamp = LocalDateTime.parse("2025-05-10T10:03:23");
        entry.user = "user002";
        entry.type = OperationType.TRANSFERRED;
        entry.amount = 990.00;
        entry.relatedUser = "user001";

        String result = Main.formatEntry(entry);
        assertThat(result).isEqualTo("[2025-05-10 10:03:23] user002 transferred 990.00 to user001");
    }

    @Test
    void formatEntry_Withdrew_FormatsCorrectly() {
        LogEntry entry = new LogEntry();
        entry.timestamp = LocalDateTime.parse("2025-05-10T23:55:32");
        entry.user = "user002";
        entry.type = OperationType.WITHDREW;
        entry.amount = 50.00;

        String result = Main.formatEntry(entry);
        assertThat(result).isEqualTo("[2025-05-10 23:55:32] user002 withdrew 50.00");
    }
    @Test
    void calculateBalance_WithOperations_ReturnsCorrectBalance() {
        List<LogEntry> entries = List.of(
                createEntry(OperationType.BALANCE_INQUIRY, 1000.0),
                createEntry(OperationType.TRANSFERRED, 100.0),
                createEntry(OperationType.RECIVED, 200.0),
                createEntry(OperationType.WITHDREW, 50.0)
        );

        double initialBalance = entries.stream()
                .filter(e -> e.type == OperationType.BALANCE_INQUIRY)
                .findFirst()
                .map(e -> e.amount)
                .orElse(0.0);

        double balance = initialBalance;
        for (LogEntry e : entries) {
            switch (e.type) {
                case TRANSFERRED, WITHDREW -> balance -= e.amount;
                case RECIVED -> balance += e.amount;
            }
        }

        assertThat(balance).isEqualTo(1000.0 - 100.0 + 200.0 - 50.0);
    }

    private LogEntry createEntry(OperationType type, double amount) {
        LogEntry entry = new LogEntry();
        entry.type = type;
        entry.amount = amount;
        return entry;
    }
}
