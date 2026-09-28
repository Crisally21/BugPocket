package ru.codex.codextest.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VideoPreviewProcessTests {
    @TempDir Path directory;

    @Test
    void rejectsWrongWorkerArguments() {
        assertEquals(2, VideoPreviewWorker.run(new String[0]));
        assertEquals(2, VideoPreviewWorker.run(new String[]{"one"}));
        assertEquals(2, VideoPreviewWorker.run(new String[]{"one", "two", "three"}));
    }

    @Test
    void killsTimedOutWorkerAndCleansAllFiles() throws Exception {
        Path work = Files.createDirectory(directory.resolve("work"));
        Path pid = directory.resolve("worker.pid");
        var process = fakeProcess("hang", Duration.ofSeconds(3), pid);
        var generator = new VideoPreviewGenerator(work, process);
        long started = System.nanoTime();
        IOException error = assertThrows(IOException.class,
                () -> generator.generatePreview(new ByteArrayInputStream(new byte[]{1})));
        assertTrue(error.getMessage().contains("Превышено время"));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 8);
        long workerPid = Long.parseLong(Files.readString(pid));
        assertFalse(ProcessHandle.of(workerPid).map(ProcessHandle::isAlive).orElse(false));
        assertEmpty(work);
    }

    @Test
    void stopsWorkerOnInterruptionAndPreservesInterruptFlag() throws Exception {
        Path work = Files.createDirectory(directory.resolve("work"));
        Path pid = directory.resolve("worker.pid");
        var generator = new VideoPreviewGenerator(work, fakeProcess("hang", Duration.ofSeconds(30), pid));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                generator.generatePreview(new ByteArrayInputStream(new byte[]{1}));
            } catch (Throwable e) {
                failure.set(e);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!Files.exists(pid) && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue(Files.exists(pid), "Тестовый worker должен запуститься");
        } finally {
            caller.interrupt();
            caller.join(5000);
        }
        assertFalse(caller.isAlive());
        assertInstanceOf(InterruptedException.class, failure.get());
        assertTrue(interrupted.get());
        long workerPid = Long.parseLong(Files.readString(pid));
        assertFalse(ProcessHandle.of(workerPid).map(ProcessHandle::isAlive).orElse(false));
        assertEmpty(work);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exit", "missing", "garbage", "oversized"})
    void rejectsFailedOrInvalidWorkerResultAndCleansFiles(String mode) throws Exception {
        Path work = Files.createDirectory(directory.resolve("work"));
        var generator = new VideoPreviewGenerator(work,
                fakeProcess(mode, Duration.ofSeconds(10), directory.resolve("worker.pid")));
        IOException error = assertThrows(IOException.class,
                () -> generator.generatePreview(new ByteArrayInputStream(new byte[]{1})));
        assertTrue(error.getMessage().contains(mode.equals("exit") ? "Не удалось создать" : "корректное PNG"));
        assertEmpty(work);
    }

    @Test
    void cleansInputWhenJavaCannotStart() throws Exception {
        var process = new VideoPreviewProcess(Duration.ofSeconds(10),
                (input, output) -> List.of(directory.resolve("missing-java.exe").toString()));
        var generator = new VideoPreviewGenerator(directory, process);
        IOException error = assertThrows(IOException.class,
                () -> generator.generatePreview(new ByteArrayInputStream(new byte[]{1})));
        assertEquals("Не удалось запустить обработчик видео", error.getMessage());
        assertEmpty(directory);
    }

    private VideoPreviewProcess fakeProcess(String mode, Duration timeout, Path pid) {
        return new VideoPreviewProcess(timeout, (input, output) -> {
            List<String> command = new ArrayList<>(VideoPreviewProcess.workerCommand(input, output));
            int main = command.indexOf(VideoPreviewWorker.class.getName());
            command.set(main, FakeWorker.class.getName());
            command.add(mode);
            command.add(pid.toString());
            return command;
        });
    }

    @Test
    void reportsUnavailableNativeRuntimeAndCleansFiles() throws Exception {
        Path work = Files.createDirectory(directory.resolve("work"));
        var runner = new VideoPreviewProcess(Duration.ofSeconds(10), (input, output) -> {
            List<String> command = new ArrayList<>(VideoPreviewProcess.workerCommand(input, output));
            // Нет JavaCV и FFmpeg в classpath: настоящий worker должен сообщить ошибку runtime.
            command.set(command.indexOf("-cp") + 1, Path.of("target/classes").toAbsolutePath().toString());
            return command;
        });
        var generator = new VideoPreviewGenerator(work, runner);
        IOException error = assertThrows(IOException.class,
                () -> generator.generatePreview(new ByteArrayInputStream(new byte[]{1})));
        assertEquals("Не удалось загрузить библиотеку обработки видео", error.getMessage());
        assertEmpty(work);
    }

    private static void assertEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }
    }

    public static class FakeWorker {
        public static void main(String[] args) throws Exception {
            Path output = Path.of(args[1]);
            switch (args[2]) {
                case "hang" -> {
                    Files.writeString(output, "partial PNG");
                    Files.writeString(VideoPreviewWorker.errorFile(output), "partial error");
                    Files.writeString(Path.of(args[3]), Long.toString(ProcessHandle.current().pid()));
                    Thread.sleep(60_000);
                }
                case "exit" -> System.exit(7);
                case "garbage" -> Files.writeString(output, "not PNG");
                case "oversized" -> Files.write(output, new byte[4_000_001]);
                case "missing" -> { }
                default -> throw new IllegalArgumentException(args[2]);
            }
        }
    }
}
