package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import ru.codex.codextest.model.AttachmentFileType;

import static org.assertj.core.api.Assertions.assertThat;

public class AttachmentFileTypeTests {

    @Test
    void recognizesJpgExtension() {
        assertThat(AttachmentFileType.fromExtension("jpg"))
                .isEqualTo(AttachmentFileType.JPG);
    }

    @Test
    void fromFileNameTest() {
        assertThat(AttachmentFileType.fromFilename("photo.jpg"))
                .isEqualTo(AttachmentFileType.JPG);
    }
}
