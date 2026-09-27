package ru.codex.codextest.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class VideoPreviewGenerator {
    private final Path temporaryDirectory;
    private static final long MAX_VIDEO_SIZE_BYTES = 25000000L;

    public VideoPreviewGenerator(Path temporaryDirectory) {
        this.temporaryDirectory = temporaryDirectory;
    }

    public VideoPreviewGenerator() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    public byte[] generatePreview(InputStream video) throws Exception {
        if (video == null) {
            throw new IllegalArgumentException("Видеопоток не передан");
        }

        Path temporaryFile = Files.createTempFile(temporaryDirectory, "bugpocket-video", ".mp4");
        FFmpegFrameGrabber grabber = null;
        try {
            copyVideo(video, temporaryFile);
            grabber = new FFmpegFrameGrabber(temporaryFile.toFile());
            grabber.start();
            var frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("Не удалось получить кадр из видео");
            }

            try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
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
            }
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

    private void copyVideo(InputStream video, Path target) throws IOException {
        var buffer = new byte[8192];
        long totalBytes = 0;
        int bytesRead;

        try (OutputStream output = Files.newOutputStream(target)) {
            while ((bytesRead = video.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_VIDEO_SIZE_BYTES) {
                    throw new IOException("Размер видео превышает 25 MB");
                } else {
                    output.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}
