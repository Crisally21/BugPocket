package ru.codex.codextest;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class VideoPreviewGeneratorTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsFirstFrameFromMp4() throws Exception {
        var videoUrl = getClass().getResource("/video/linkin-park-given-up_113376 - Trim.mp4");
        assertNotNull(videoUrl);
        var videoPath = Path.of(videoUrl.toURI()).toString();
        var grabber = new FFmpegFrameGrabber(videoPath);
        try {
            grabber.start();
            var frame = grabber.grabImage();
            assertNotNull(frame);

            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.convert(frame);
            assertNotNull(image);

            Path previewPath = temporaryDirectory.resolve("preview.png");
            assertTrue(ImageIO.write(image, "png", previewPath.toFile()));
            assertNotNull(ImageIO.read(previewPath.toFile()));
        } finally {
            grabber.release();
        }
    }
}
