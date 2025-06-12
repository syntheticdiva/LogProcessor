package org.example;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        Path inputDir = Paths.get(System.getProperty("user.dir"), "logs");
        Path outputDir = inputDir.resolve("transactions_by_users");

        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        System.out.println("Input directory: " + inputDir.toAbsolutePath());
        System.out.println("Output directory: " + outputDir.toAbsolutePath());

        if (!Files.exists(inputDir)) {
            System.err.println("Directory 'logs' not found!");
            return;
        }

        if (Files.exists(outputDir)) {
            Files.walk(outputDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
        Files.createDirectories(outputDir);

        Map<String, List<LogEntry>> userEntries = new HashMap<>();

        try (Stream<Path> files = Files.list(inputDir)) {
            files.filter(f -> f.toString().endsWith(".log"))
                    .forEach(file -> processFile(file, userEntries));
        }

        for (Map.Entry<String, List<LogEntry>> entry : userEntries.entrySet()) {
            String user = entry.getKey();
            List<LogEntry> entries = entry.getValue();

            entries.sort(Comparator.comparing(LogEntry::getTimestamp));

            double initialBalance = 0.0;
            Optional<LogEntry> firstBalance = entries.stream()
                    .filter(e -> e.getType() == OperationType.BALANCE_INQUIRY)
                    .findFirst();
            if (firstBalance.isPresent()) {
                initialBalance = firstBalance.get().getAmount();
            }

            double balance = initialBalance;
            for (LogEntry logEntry : entries) {
                switch (logEntry.getType()) {
                    case RECIVED:
                        balance += logEntry.getAmount();
                        break;
                    case TRANSFERRED:
                    case WITHDREW:
                        balance -= logEntry.getAmount();
                        break;
                }
            }

            String finalBalanceLine = String.format("[%s] %s final balance %.2f",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    user, balance);

            Path userFile = outputDir.resolve(user + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(userFile)) {
                for (LogEntry logEntry : entries) {
                    writer.write(formatEntry(logEntry));
                    writer.newLine();
                }
                writer.write(finalBalanceLine);
                writer.newLine();
            }
        }
    }

    public static String formatEntry(LogEntry entry) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String base = String.format("[%s] %s ",
                entry.getTimestamp().format(formatter),
                entry.getUser());

        switch (entry.getType()) {
            case BALANCE_INQUIRY:
                return base + String.format(Locale.US, "balance inquiry %.2f", entry.getAmount());
            case TRANSFERRED:
                return base + String.format(Locale.US, "transferred %.2f to %s", entry.getAmount(), entry.getRelatedUser());
            case RECIVED:
                return base + String.format(Locale.US, "received %.2f from %s", entry.getAmount(), entry.getRelatedUser());
            case WITHDREW:
                return base + String.format(Locale.US, "withdrew %.2f", entry.getAmount());
            default:
                return "";
        }
    }

    private static void processFile(Path file, Map<String, List<LogEntry>> userEntries) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, userEntries);
            }
        } catch (IOException e) {
            System.err.printf("File reading error %s: %s%n", file.getFileName(), e.getMessage());
        }
    }

    public static void processLine(String line, Map<String, List<LogEntry>> userEntries) {
        Pattern pattern = Pattern.compile(
                "^\\[(.*?)]\\s+(\\w+)\\s+(balance inquiry|transferred|received|withdrew)\\s+([0-9]+(?:\\.[0-9]+)?)(?:\\s+to\\s+(\\w+))?$"
        );
        Matcher matcher = pattern.matcher(line);

        if (!matcher.matches()) {
            System.err.println("Invalid line: " + line);
            return;
        }
        String timestampStr = matcher.group(1);
        String user = matcher.group(2);
        String operation = matcher.group(3);
        String amountStr = matcher.group(4);
        String targetUser = matcher.group(5);

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(
                    timestampStr,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );
        } catch (Exception e) {
            System.err.println("Invalid date format: " + timestampStr);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid amount: " + amountStr);
            return;
        }

        LogEntry entry = new LogEntry();
        entry.setTimestamp(timestamp);
        entry.setUser(user);
        entry.setAmount(amount);
        entry.setRelatedUser(targetUser);

        switch (operation) {
            case "balance inquiry":
                entry.setType(OperationType.BALANCE_INQUIRY);
                break;
            case "transferred":
                entry.setType(OperationType.TRANSFERRED);
                break;
            case "withdrew":
                entry.setType(OperationType.WITHDREW);
                break;
            case "received":
                entry.setType(OperationType.RECIVED);
                break;
            default:
                System.err.println("Unknown operation: " + operation);
                return;
        }

        userEntries.computeIfAbsent(user, k -> new ArrayList<>()).add(entry);

        if (entry.getType() == OperationType.TRANSFERRED && targetUser != null) {
            LogEntry recivedEntry = new LogEntry();
            recivedEntry.setTimestamp(timestamp);
            recivedEntry.setUser(user);
            recivedEntry.setType(OperationType.RECIVED);
            recivedEntry.setAmount(amount);
            recivedEntry.setRelatedUser(user);
            userEntries.computeIfAbsent(targetUser, k -> new ArrayList<>()).add(recivedEntry);
        }
    }
}
