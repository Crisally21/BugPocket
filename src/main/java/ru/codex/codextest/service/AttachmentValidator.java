package ru.codex.codextest.service;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class AttachmentValidator {

    private static final long MAX_FILE_SIZE_BYTES = 25000000L;

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
        validateNotEmpty(file);
        validateSize(file);
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Содержимое файла не является изображением");
            }
            ImageReader reader = readers.next();

            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("PNG") && !format.equalsIgnoreCase("JPEG")) {
                    throw new IllegalArgumentException("Содержимое файла не является изображением");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
