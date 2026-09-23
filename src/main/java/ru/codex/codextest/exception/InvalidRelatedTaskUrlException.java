package ru.codex.codextest.exception;

public class InvalidRelatedTaskUrlException extends RuntimeException {
    public InvalidRelatedTaskUrlException() {
        super("Укажи корректную HTTP/HTTPS ссылку на задачу");
    }
}
