package ru.codex.codextest.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Запуск после package: mvnw -Dtest=PackagedVideoPreviewIT test. */
public class PackagedVideoPreviewIT {
    @TempDir Path directory;

    @Test
    void packagedApplicationStartsWorkerWithoutDatabaseOrSpringContext() throws Exception {
        Path jar = Path.of("target/codextest-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Сначала выполни Maven package");
        Path output = directory.resolve("preview.png");
        Path work = Files.createDirectory(directory.resolve("временная папка"));
        Path log = directory.resolve("run.log");
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        String java = (Files.exists(javaBin.resolve("javaw.exe"))
                ? javaBin.resolve("javaw.exe") : javaBin.resolve("java")).toString();
        // Probe загружается снаружи, а production-классы и зависимости — из BOOT-INF собранного JAR.
        var builder = new ProcessBuilder(List.of(java,
                "-Dloader.main=" + Probe.class.getName(),
                "-Dloader.path=" + Path.of("target/test-classes").toAbsolutePath(),
                "-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher",
                Path.of("src/test/resources/video/sample.mov").toAbsolutePath().toString(),
                output.toString(), work.toString()));
        builder.environment().remove("DB_PASSWORD");
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(40, TimeUnit.SECONDS), "Запуск из JAR завис");
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            process.waitFor();
        }
        var preview = ImageIO.read(output.toFile());
        assertNotNull(preview);
        assertEquals(64, preview.getWidth());
        assertEquals(48, preview.getHeight());
        try (var files = Files.list(work)) {
            assertEquals(0, files.count());
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            var home = new org.springframework.boot.system.ApplicationHome(VideoPreviewGenerator.class);
            if (home.getSource() == null || !home.getSource().isFile()) {
                throw new IllegalStateException("Production-класс должен быть загружен из JAR");
            }
            try (var input = Files.newInputStream(Path.of(args[0]))) {
                Files.write(Path.of(args[1]), new VideoPreviewGenerator(Path.of(args[2])).generatePreview(input));
            }
        }
    }
}
