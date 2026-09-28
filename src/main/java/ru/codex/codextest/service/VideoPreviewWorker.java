package ru.codex.codextest.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class VideoPreviewWorker {
    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Нужно передать путь к видео и путь к превью");
            return 2;
        }
        Path output = Path.of(args[1]);
        try {
            byte[] preview = new VideoPreviewGenerator().generatePreviewFromFile(Path.of(args[0]));
            Files.write(output, preview);
            return 0;
        } catch (Exception | LinkageError failure) {
            // Передаём только известные сообщения, без стектрейса и локальных путей.
            String message = switch (String.valueOf(failure.getMessage())) {
                case "Не удалось получить кадр из видео" -> "Не удалось получить кадр из видео";
                case "Размер видеокадра превышает 20 миллионов пикселей" ->
                        "Размер видеокадра превышает 20 миллионов пикселей";
                default -> failure instanceof LinkageError
                        ? "Не удалось загрузить библиотеку обработки видео"
                        : "Не удалось создать превью видео";
            };
            try {
                Files.writeString(errorFile(output), message, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // Родитель обработает ненулевой код даже при недоступном файле ошибки.
            }
            return 1;
        }
    }

    static Path errorFile(Path output) {
        return output.resolveSibling(output.getFileName() + ".error");
    }
}
