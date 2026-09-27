package ru.codex.codextest;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import ru.codex.codextest.service.VideoPreviewGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class VideoPreviewGeneratorTests {
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
}
