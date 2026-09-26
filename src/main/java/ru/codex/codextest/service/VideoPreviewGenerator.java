package ru.codex.codextest.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class VideoPreviewGenerator {

    public byte[] generatePreview(InputStream video) throws Exception {
        if (video == null) {
            throw new IllegalArgumentException("Видеопоток не передан");
        }

        Path temporaryFile = Files.createTempFile("bugpocket-video", ".mp4");
        FFmpegFrameGrabber grabber = null;
        try {
            Files.copy(video, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            grabber = new FFmpegFrameGrabber(temporaryFile.toFile());
            grabber.start();
            var frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("Не удалось получить кадр из видео");
            }

            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.convert(frame);
            if (image == null) {
                throw new IOException("Не удалось преобразовать кадр видео в изображение");
            }

            ByteArrayOutputStream preview = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, "png", preview);
            if (!written) {
                throw new IOException("Не удалось записать превью в PNG");
            }
            return preview.toByteArray();
        } finally {
            try {
                if (grabber != null) {
                    grabber.release();
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

}
