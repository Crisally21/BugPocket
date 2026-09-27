package ru.codex.codextest.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class VideoPreviewGenerator {
    private static final int MAX_PREVIEW_WIDTH = 1280;
    private static final int MAX_PREVIEW_HEIGHT = 720;
    private final Path temporaryDirectory;
    private static final long MAX_VIDEO_SIZE_BYTES = 25000000L;
    private static final long MAX_VIDEO_PIXELS = 20000000L;

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
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            if (width <= 0 || height <= 0) {
                throw new IOException("Не удалось получить кадр из видео");
            }
            long pixels = (long) width * height;
            if (pixels > MAX_VIDEO_PIXELS) {
                throw new IOException("Размер видеокадра превышает 20 миллионов пикселей");
            }
            var frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("Не удалось получить кадр из видео");
            }

            try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                BufferedImage image = converter.convert(frame);
                if (image == null) {
                    throw new IOException("Не удалось преобразовать кадр видео в изображение");
                }

                image = resizePreview(image);
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

    private BufferedImage resizePreview(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
            return source;
        }
        double scale = Math.min(
                (double) MAX_PREVIEW_WIDTH / width,
                (double) MAX_PREVIEW_HEIGHT / height
        );
        int targetWidth = Math.max(1, (int) (width * scale));
        int targetHeight = Math.max(1, (int) (height * scale));
        BufferedImage resized = new BufferedImage(
                targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        return resized;
    }
}
