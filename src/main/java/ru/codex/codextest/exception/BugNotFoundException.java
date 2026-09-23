package ru.codex.codextest.exception;

public class BugNotFoundException extends RuntimeException {
    public BugNotFoundException(long id) {
        super("Баг с номером " + id + " не найден");
    }
}
