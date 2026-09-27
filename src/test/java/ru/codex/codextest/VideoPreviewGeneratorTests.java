package ru.codex.codextest;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import ru.codex.codextest.service.VideoPreviewGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class VideoPreviewGeneratorTests {
    @ParameterizedTest(name = "Создание PNG-превью из {0}")
    @ValueSource(strings = {"mov", "mkv", "avi", "webm"})
    void generatesPngPreviewFromSupportedContainer(String extension) throws Exception {
        try (InputStream video = getClass().getResourceAsStream("/video/sample." + extension)) {
            assertNotNull(video, "Не найден тестовый ролик: " + extension);

            byte[] previewBytes = new VideoPreviewGenerator().generatePreview(video);
            assertNotNull(previewBytes);
            byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            assertArrayEquals(pngSignature, Arrays.copyOf(previewBytes, pngSignature.length));

            BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewBytes));
            assertNotNull(preview);
            assertEquals(64, preview.getWidth());
            assertEquals(48, preview.getHeight());
        }
    }

    @Test
    void generatesPngPreviewFromMp4() throws Exception {
        try (InputStream video = getClass().getResourceAsStream("/video/linkin-park-given-up_113376 - Trim.mp4")) {
            assertNotNull(video);

            byte[] previewBytes = new VideoPreviewGenerator().generatePreview(video);
            assertNotNull(previewBytes);

            BufferedImage preview = ImageIO.read(new ByteArrayInputStream(previewBytes));
            assertNotNull(preview);
        }
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
    void rejectsVideoLargerThanLimit() {
        var video = new VideoPreviewGenerator();
        var bytes = new byte[25000001];
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        IOException exception = assertThrows(IOException.class, () -> video.generatePreview(byteArrayInputStream));
        assertEquals("Размер видео превышает 25 MB", exception.getMessage());
    }

    @Test
    void passesSizeCheckAtExactLimit() {
        var bytes = new byte[25000000];
        var video = new VideoPreviewGenerator();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        assertThrows(FFmpegFrameGrabber.Exception.class, () -> video.generatePreview(byteArrayInputStream));
    }
}
