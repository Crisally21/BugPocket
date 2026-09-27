package ru.codex.codextest;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import ru.codex.codextest.service.VideoPreviewGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class VideoPreviewGeneratorTests {

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest(name = "Создание PNG-превью из {0}")
    @ValueSource(strings = {"mov", "mkv", "avi", "webm"})
    void generatesPngPreviewFromSupportedContainer(String extension) throws Exception {
        try (InputStream video = getClass().getResourceAsStream("/video/sample." + extension)) {
            assertNotNull(video, "Не найден тестовый ролик: " + extension);

            byte[] previewBytes = new VideoPreviewGenerator(temporaryDirectory).generatePreview(video);
            assertNotNull(previewBytes);
            byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            assertArrayEquals(pngSignature, Arrays.copyOf(previewBytes, pngSignature.length));

            BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewBytes));
            assertNotNull(preview);
            assertEquals(64, preview.getWidth());
            assertEquals(48, preview.getHeight());

            try (var files = Files.list(temporaryDirectory)) {
                assertEquals(0L, files.count(), "После создания превью временная папка должна быть пустой");
            }
        }
    }

    @Test
    void generatesPngPreviewFromMp4() throws Exception {
        try (InputStream video = getClass().getResourceAsStream("/video/linkin-park-given-up_113376 - Trim.mp4")) {
            assertNotNull(video);

            byte[] previewBytes = new VideoPreviewGenerator(temporaryDirectory).generatePreview(video);
            assertNotNull(previewBytes);

            BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewBytes));
            assertNotNull(preview);
        }
        assertTemporaryDirectoryEmpty();
    }

    @Test
    void rejectsNonVideoContent() {
        String string = "Это текст, а не видео";
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        VideoPreviewGenerator videoPreviewGenerator = new VideoPreviewGenerator();
        assertThrows(
                FFmpegFrameGrabber.Exception.class,
                () -> videoPreviewGenerator.generatePreview(byteArrayInputStream)
        );
    }

    @Test
    void rejectsNullVideo() {
        VideoPreviewGenerator video = new VideoPreviewGenerator();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> video.generatePreview(null));
        assertEquals("Видеопоток не передан", exception.getMessage());
    }

    @Test
    void rejectsEmptyVideo() {
        var bytes = new byte[0];
        VideoPreviewGenerator videoPreviewGenerator = new VideoPreviewGenerator();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        assertThrows(
                FFmpegFrameGrabber.Exception.class,
                () -> videoPreviewGenerator.generatePreview(byteArrayInputStream)
        );
    }

    @Test
    void rejectsVideoLargerThanLimit() throws IOException {
        var video = new VideoPreviewGenerator(temporaryDirectory);
        var bytes = new byte[25000001];
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        IOException exception = assertThrows(IOException.class, () -> video.generatePreview(byteArrayInputStream));
        assertEquals("Размер видео превышает 25 MB", exception.getMessage());
        assertTemporaryDirectoryEmpty();
    }

    @Test
    void passesSizeCheckAtExactLimit() {
        var bytes = new byte[25000000];
        var video = new VideoPreviewGenerator();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        assertThrows(FFmpegFrameGrabber.Exception.class, () -> video.generatePreview(byteArrayInputStream));
    }

    @Test
    void propagatesVideoReadFailure() {
        var video = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Ошибка чтения текстового потока");
            }
        };
        var videoPreviewGenerator = new VideoPreviewGenerator();
        IOException exception = assertThrows(IOException.class, () -> videoPreviewGenerator.generatePreview(video));
        assertEquals("Ошибка чтения текстового потока", exception.getMessage());
    }

    @Test
    void deletesTemporaryFileAfterReadFailure() throws IOException {
        var video = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Ошибка чтения текстового потока");
            }
        };
        var videoPreviewGenerator = new VideoPreviewGenerator(temporaryDirectory);
        assertThrows(IOException.class, () -> videoPreviewGenerator.generatePreview(video));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(0L, files.count());
        }

    }

    @Test
    void deletesTemporaryFileAfterDecodeFailure() throws IOException {
        byte[] bytes = "Это текст, а не видео".getBytes(StandardCharsets.UTF_8);
        try (var video = new ByteArrayInputStream(bytes)) {
            var generator = new VideoPreviewGenerator(temporaryDirectory);
            assertThrows(FFmpegFrameGrabber.Exception.class, () -> generator.generatePreview(video));
        }
        assertTemporaryDirectoryEmpty();
    }

    @ParameterizedTest(name = "Настоящее MOV размером {0} байт")
    @ValueSource(ints = {24_999_999, 25_000_000, 25_000_001})
    void handlesRealVideoAtSizeBoundary(int size) throws Exception {
        byte[] bytes = movWithFreeAtom(size);
        assertEquals(size, bytes.length);
        var generator = new VideoPreviewGenerator(temporaryDirectory);
        try (var video = new ByteArrayInputStream(bytes)) {
            if (size > 25_000_000) {
                IOException exception = assertThrows(IOException.class, () -> generator.generatePreview(video));
                assertEquals("Размер видео превышает 25 MB", exception.getMessage());
            } else {
                byte[] previewBytes = generator.generatePreview(video);
                BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewBytes));
                assertNotNull(preview);
                assertEquals(64, preview.getWidth());
                assertEquals(48, preview.getHeight());
            }
        }
        assertTemporaryDirectoryEmpty();
    }

    @Test
    void rejectsAudioOnlyContainerAndDeletesTemporaryFile() throws Exception {
        try (InputStream audio = getClass().getResourceAsStream("/video/audio-only.m4a")) {
            assertNotNull(audio);
            var generator = new VideoPreviewGenerator(temporaryDirectory);
            IOException exception = assertThrows(IOException.class, () -> generator.generatePreview(audio));
            assertEquals("Не удалось получить кадр из видео", exception.getMessage());
        }
        assertTemporaryDirectoryEmpty();
    }

    @Test
    void deletesPartiallyWrittenFileAfterReadFailure() throws IOException {
        IOException failure = new IOException("Поток оборвался после двух порций");
        try (InputStream video = new InputStream() {
            private int remaining = 16_384;

            @Override
            public int read() throws IOException {
                if (remaining == 0) {
                    assertPartiallyWrittenFile();
                    throw failure;
                }
                remaining--;
                return 1;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (length == 0) {
                    return 0;
                }
                if (remaining == 0) {
                    assertPartiallyWrittenFile();
                    throw failure;
                }
                int count = Math.min(length, remaining);
                Arrays.fill(buffer, offset, offset + count, (byte) 1);
                remaining -= count;
                return count;
            }

            private void assertPartiallyWrittenFile() throws IOException {
                try (var files = Files.list(temporaryDirectory)) {
                    var paths = files.toList();
                    assertEquals(1, paths.size());
                    assertEquals(16_384L, Files.size(paths.get(0)));
                }
            }
        }) {
            var generator = new VideoPreviewGenerator(temporaryDirectory);
            IOException actual = assertThrows(IOException.class, () -> generator.generatePreview(video));
            assertSame(failure, actual);
        }
        assertTemporaryDirectoryEmpty();
    }

    private byte[] movWithFreeAtom(int size) throws IOException {
        try (InputStream source = getClass().getResourceAsStream("/video/sample.mov")) {
            assertNotNull(source);
            byte[] original = source.readAllBytes();
            int paddingSize = size - original.length;
            assertTrue(paddingSize >= 8);
            byte[] padded = Arrays.copyOf(original, size);
            // Допустимый MOV-атом свободного места: длина (big-endian), тип, заполнение.
            // Добавляется в конец и не меняет смещения существующих видеоданных.
            ByteBuffer.wrap(padded, original.length, 8)
                      .putInt(paddingSize)
                      .put("free".getBytes(StandardCharsets.US_ASCII));
            return padded;
        }
    }

    private void assertTemporaryDirectoryEmpty() throws IOException {
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(0L, files.count(), "Временные файлы должны быть удалены");
        }
    }

    @Test
    void acceptsVideoAtPixelLimit() throws Exception {
        try (InputStream video = getClass().getResourceAsStream("/video/frame-5000x4000.mkv")) {
            assertNotNull(video);
            var videoPreviewGenerator = new VideoPreviewGenerator(temporaryDirectory);
            byte[] videoBytes = videoPreviewGenerator.generatePreview(video);
            assertNotNull(ImageIO.read(new ByteArrayInputStream(videoBytes)));
            assertTemporaryDirectoryEmpty();
        }
    }

    @Test
    void rejectsVideoAbovePixelLimit() throws Exception {
        try (InputStream videoStream = getClass().getResourceAsStream("/video/frame-5000x4002.mkv")) {
            assertNotNull(videoStream);
            var video = new VideoPreviewGenerator(temporaryDirectory);
            IOException exception = assertThrows(IOException.class, () -> video.generatePreview(videoStream));
            assertEquals("Размер видеокадра превышает 20 миллионов пикселей", exception.getMessage());
            assertTemporaryDirectoryEmpty();
        }
    }
}
