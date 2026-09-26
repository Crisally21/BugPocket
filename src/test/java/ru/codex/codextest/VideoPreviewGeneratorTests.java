package ru.codex.codextest;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import ru.codex.codextest.service.VideoPreviewGenerator;

import static org.junit.jupiter.api.Assertions.assertNotNull;


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
}
