package ru.codex.codextest.service;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class AttachmentValidator {

    private static final long MAX_FILE_SIZE_BYTES = 25000000L;
    private static final long MAX_IMAGE_PIXELS = 20000000L;

    public void validateNotEmpty(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("Файл не передан");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
    }

    public void validateSize(MultipartFile file) {
        validateNotEmpty(file);
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Размер файла превышает 25 MB");
        }
    }

    public void validateImageContent(MultipartFile file) {
        validateSize(file);
        try (InputStream inputStream = file.getInputStream()) {
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
            if (imageInputStream == null) {
                throw new IllegalArgumentException("Не удалось прочитать изображение");
            }

            try (imageInputStream) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
                if (!readers.hasNext()) {
                    throw new IllegalArgumentException("Содержимое файла не является изображением");
                }

                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("PNG")
                            && !format.equalsIgnoreCase("JPG")
                            && !format.equalsIgnoreCase("JPEG")) {
                        throw new IllegalArgumentException("Содержимое файла не является изображением");
                    }

                    reader.setInput(imageInputStream);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                        throw new IllegalArgumentException("Изображение превышает допустимый размер");
                    }

                    BufferedImage image = reader.read(0);
                    if (image == null) {
                        throw new IllegalArgumentException("Содержимое файла не является изображением");
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать изображение", e);
        }
    }

    public void validatePdfContent(MultipartFile file) {
        validateSize(file);
        byte[] expectedHeader = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        try (InputStream inputStream = file.getInputStream()) {
            byte[] actualHeader = inputStream.readNBytes(expectedHeader.length);
            if (!Arrays.equals(expectedHeader, actualHeader)) {
                throw new IllegalArgumentException("Содержимое файла не является PDF файлом");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать PDF-файл", e);
        }
    }
}
