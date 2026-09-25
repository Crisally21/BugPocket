package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.codex.codextest.service.AttachmentValidator;

public class AttachmentValidatorTests {

    @Test
    void acceptsFileAtSizeLimit() {
        AttachmentValidator validator = new AttachmentValidator();
        var file = new MockMultipartFile("file", "screen.png", "image/png", new byte[25000000]);
        validator.validateSize(file);
    }
}
