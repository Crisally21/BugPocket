package ru.codex.codextest.service;

import org.springframework.boot.system.ApplicationHome;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Управляет временем жизни worker; декодирование выполняется только в дочерней JVM. */
final class VideoPreviewProcess {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_PNG_BYTES = 4_000_000;
    private final Duration timeout;
    private final CommandFactory commandFactory;

    @FunctionalInterface
    interface CommandFactory {
        List<String> command(Path input, Path output) throws IOException;
    }

    VideoPreviewProcess() {
        this(TIMEOUT, VideoPreviewProcess::workerCommand);
    }

    VideoPreviewProcess(Duration timeout, CommandFactory commandFactory) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Тайм-аут должен быть положительным");
        }
        this.timeout = timeout;
        this.commandFactory = commandFactory;
    }

    byte[] generate(Path input, Path temporaryDirectory) throws IOException, InterruptedException {
        long started = System.nanoTime();
        Path job = Files.createTempDirectory(temporaryDirectory, "bugpocket-preview-");
        Path output = job.resolve("preview.png");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(commandFactory.command(input, output));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            try {
                process = builder.start();
            } catch (IOException failure) {
                throw new IOException("Не удалось запустить обработчик видео", failure);
            }
            process.getOutputStream().close();
            long remaining = timeout.toNanos() - (System.nanoTime() - started);
            if (remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                throw new IOException("Превышено время создания превью видео (10 секунд)");
            }
            if (process.exitValue() != 0) {
                throw new IOException(readWorkerError(output));
            }
            return readPreview(output);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            // Дождаться фактического завершения обязательно: FFmpeg может держать файл открытым.
            if (process != null) {
                stopAndWait(process);
            }
            try {
                Files.deleteIfExists(output);
            } finally {
                try {
                    Files.deleteIfExists(VideoPreviewWorker.errorFile(output));
                } finally {
                    Files.deleteIfExists(job);
                }
            }
        }
    }

    private static void stopAndWait(Process process) {
        boolean interrupted = Thread.interrupted();
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            while (true) {
                try {
                    process.waitFor();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static List<String> workerCommand(Path input, Path output) throws IOException {
        Path javaHome = Path.of(System.getProperty("java.home"), "bin");
        // javaw не открывает консольное окно на Windows.
        Path java = Files.isRegularFile(javaHome.resolve("javaw.exe"))
                ? javaHome.resolve("javaw.exe") : javaHome.resolve("java");
        if (!Files.isRegularFile(java)) {
            throw new IOException("Не найден исполняемый файл Java для обработки видео");
        }
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Xmx256m",
                "-Djava.awt.headless=true"));
        var source = new ApplicationHome(VideoPreviewGenerator.class).getSource();
        if (source != null && source.isFile() && source.getName().endsWith(".jar")) {
            command.addAll(List.of("-jar", source.getAbsolutePath(), "--video-preview-worker"));
        } else {
            command.addAll(List.of("-cp", System.getProperty("java.class.path"),
                    VideoPreviewWorker.class.getName()));
        }
        command.add(input.toAbsolutePath().toString());
        command.add(output.toAbsolutePath().toString());
        return command;
    }

    private static String readWorkerError(Path output) throws IOException {
        Path error = VideoPreviewWorker.errorFile(output);
        if (Files.isRegularFile(error) && Files.size(error) <= 512) {
            String message = Files.readString(error, StandardCharsets.UTF_8);
            if (List.of("Не удалось получить кадр из видео",
                    "Размер видеокадра превышает 20 миллионов пикселей",
                    "Не удалось загрузить библиотеку обработки видео").contains(message)) {
                return message;
            }
        }
        return "Не удалось создать превью видео";
    }

    private static byte[] readPreview(Path output) throws IOException {
        if (!Files.isRegularFile(output) || Files.size(output) == 0 || Files.size(output) > MAX_PNG_BYTES) {
            throw new IOException("Обработчик не создал корректное PNG-превью");
        }
        // Проверяем заголовок и размеры до декодирования результата в родительской JVM.
        try (var stream = ImageIO.createImageInputStream(output.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Обработчик не создал корректное PNG-превью");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if (!"PNG".equalsIgnoreCase(reader.getFormatName())
                        || reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                        || reader.getWidth(0) > 1280 || reader.getHeight(0) > 720
                        || reader.read(0) == null) {
                    throw new IOException("Обработчик не создал корректное PNG-превью");
                }
            } finally {
                reader.dispose();
            }
        }
        return Files.readAllBytes(output);
    }
}
