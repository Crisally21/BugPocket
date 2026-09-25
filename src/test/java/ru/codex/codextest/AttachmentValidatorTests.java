package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.codex.codextest.service.AttachmentValidator;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class AttachmentValidatorTests {

    @Test
    void acceptsFileAtSizeLimit() {
        AttachmentValidator validator = new AttachmentValidator();
        var file = new MockMultipartFile("file", "screen.png", "image/png", new byte[25000000]);
        validator.validateSize(file);
    }

    @Test
    void rejectsFileAboveSizeLimit() {
        AttachmentValidator validator = new AttachmentValidator();
        var file = new MockMultipartFile("file", "screen.png", "image/png", new byte[25000001]);
        assertThatThrownBy(() -> validator.validateSize(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Размер файла превышает 25 MB");
    }

    @Test
    void rejectsEmptyFile() {
        AttachmentValidator validator = new AttachmentValidator();
        var file = new MockMultipartFile("file", "screen.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> validator.validateSize(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Файл пустой");
    }

    @Test
    void rejectsMissingFile() {
        AttachmentValidator validator = new AttachmentValidator();
        assertThatThrownBy(() -> validator.validateSize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Файл не передан");
    }

    @Test
    void acceptsFileBelowSizeLimit() {
        AttachmentValidator validator = new AttachmentValidator();
        var file = new MockMultipartFile("file", "screen.png", "image/png", new byte[24999999]);
        validator.validateSize(file);

    }

    @Test
    void rejectsTextFileNameAsPng() {
        AttachmentValidator validator = new AttachmentValidator();
        var file = new MockMultipartFile("file", "pictures.png", "image/png", "это не картинка"
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> validator.validateImageContent(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Содержимое файла не является изображением");
    }

    @Test
    void acceptsValidPngImage() throws IOException {
        BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_BGR);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AttachmentValidator validator = new AttachmentValidator();
        ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
        MockMultipartFile mockMultipartFile = new MockMultipartFile("file", "picture.png", "image/png",
                                                                    byteArrayOutputStream.toByteArray());
        validator.validateImageContent(mockMultipartFile);
    }

    @Test
    void rejectsGifImage() throws IOException {
        BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_BGR);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AttachmentValidator validator = new AttachmentValidator();
        ImageIO.write(bufferedImage, "gif", byteArrayOutputStream);
        var file = new MockMultipartFile("file", "picture.gif", "image/gif",
                                                                    byteArrayOutputStream.toByteArray());
        assertThatThrownBy(() -> validator.validateImageContent(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedPng() throws IOException {
        AttachmentValidator validator = new AttachmentValidator();
        BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_BGR);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
        byte[] pngBytes = byteArrayOutputStream.toByteArray();
        byte[] truncatePng = Arrays.copyOf(pngBytes, pngBytes.length / 2);
        var file = new MockMultipartFile("file", "picture.png", "image/png", truncatePng);
        assertThatThrownBy(() -> validator.validateImageContent(file))
                .isInstanceOf(RuntimeException.class);
    }
}
