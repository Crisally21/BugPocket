package ru.codex.codextest.exception;

public class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException(long id) {
        super("Вложение с номером " + id + " не найдено");
    }
}
