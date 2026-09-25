package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.codex.codextest.service.AttachmentValidator;

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
}
