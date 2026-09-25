package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import ru.codex.codextest.model.AttachmentFileType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void fromFileNameException() {
        assertThatThrownBy(() -> AttachmentFileType.fromFilename("picture"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Некорректный файл");
    }

    @Test
    void fromFileNameExceptionTochka() {
        assertThatThrownBy(() -> AttachmentFileType.fromFilename("picture."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Некорректный файл");
    }
}
