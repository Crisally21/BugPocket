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

    @Test
    void recognizesUppercaseJpgExtension() {
        assertThat(AttachmentFileType.fromExtension("JPG"))
                .isEqualTo(AttachmentFileType.JPG);
    }

    @Test
    void recognizesJpegExtension() {
        assertThat(AttachmentFileType.fromExtension("jpeg"))
                .isEqualTo(AttachmentFileType.JPEG);
    }

    @Test
    void fromFilenameUsesLastExtension() {
        assertThat(AttachmentFileType.fromFilename("photo.backup.JPG"))
                .isEqualTo(AttachmentFileType.JPG);
    }

    @Test
    void rejectsMissingExtension() {
        assertThatThrownBy(() -> AttachmentFileType.fromExtension(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Расширение не указано");
    }

    @Test
    void rejectsMissingFilename() {
        assertThatThrownBy(() -> AttachmentFileType.fromFilename(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Название отсутствует");
    }

    @Test
    void classifiesPngAndJpegAsImages() {
        assertThat(AttachmentFileType.PNG.getMediaKind())
                .isEqualTo(AttachmentMediaKind.IMAGE);
        assertThat(AttachmentFileType.JPEG.getMediaKind())
                .isEqualTo(AttachmentMediaKind.IMAGE);
    }

    @Test
    void classifiesSupportedVideoTypesAsVideos() {
        assertThat(AttachmentFileType.MP4.getMediaKind()).isEqualTo(AttachmentMediaKind.VIDEO);
        assertThat(AttachmentFileType.MOV.getMediaKind()).isEqualTo(AttachmentMediaKind.VIDEO);
        assertThat(AttachmentFileType.MKV.getMediaKind()).isEqualTo(AttachmentMediaKind.VIDEO);
        assertThat(AttachmentFileType.AVI.getMediaKind()).isEqualTo(AttachmentMediaKind.VIDEO);
        assertThat(AttachmentFileType.WEBM.getMediaKind()).isEqualTo(AttachmentMediaKind.VIDEO);
    }

    @Test
    void mapsPngAndJpegTypesToImageContentTypes() {
        assertThat(AttachmentFileType.PNG.getContentType()).isEqualTo("image/png");
        assertThat(AttachmentFileType.JPEG.getContentType()).isEqualTo("image/jpeg");
    }
}
