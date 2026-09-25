package ru.codex.codextest;

import org.junit.jupiter.api.Test;
import ru.codex.codextest.model.AttachmentFileType;
import ru.codex.codextest.model.AttachmentMediaKind;

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

    @Test
    void fromExtensionNameInvalidName() {
        assertThatThrownBy(() -> AttachmentFileType.fromExtension("txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getContentTypeTestJpeg() {
        assertThat(AttachmentFileType.JPG.getContentType())
                .isEqualTo("image/jpeg");
    }

    @Test
    void getMediaKindTestImage() {
        assertThat(AttachmentFileType.JPG.getMediaKind())
                .isEqualTo(AttachmentMediaKind.IMAGE);
    }

    @Test
    void getMediaKindTestVideo() {
        assertThat(AttachmentFileType.MP4.getMediaKind())
                .isEqualTo(AttachmentMediaKind.VIDEO);
    }

    @Test
    void getContentTypeTestMP4() {
        assertThat(AttachmentFileType.MP4.getContentType())
                .isEqualTo("video/mp4");
    }

    @Test
    void getContentTypeTestMOV() {
        assertThat(AttachmentFileType.MOV.getContentType())
                .isEqualTo("video/quicktime");
    }

    @Test
    void getContentTypeTestMKV() {
        assertThat(AttachmentFileType.MKV.getContentType())
                .isEqualTo("video/x-matroska");
    }

    @Test
    void getContentTypeTestAVI() {
        assertThat(AttachmentFileType.AVI.getContentType())
                .isEqualTo("video/x-msvideo");
    }

    @Test
    void getContentTypeTestWEBM() {
        assertThat(AttachmentFileType.WEBM.getContentType())
                .isEqualTo("video/webm");
    }
}
