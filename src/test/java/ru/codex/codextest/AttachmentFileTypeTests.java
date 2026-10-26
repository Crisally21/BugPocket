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
        assertThatThrownBy(() -> AttachmentFileType.fromExtension("exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Формат файла не поддерживается");
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
    void mapsPngAndJpegTypesToImageContentTypes() {
        assertThat(AttachmentFileType.PNG.getContentType()).isEqualTo("image/png");
        assertThat(AttachmentFileType.JPEG.getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void recognizesDownloadOnlyFileExtensions() {
        assertThat(AttachmentFileType.fromExtension("txt")).isEqualTo(AttachmentFileType.TXT);
        assertThat(AttachmentFileType.fromExtension("log")).isEqualTo(AttachmentFileType.LOG);
        assertThat(AttachmentFileType.fromExtension("pdf")).isEqualTo(AttachmentFileType.PDF);
    }

    @Test
    void classifiesDownloadOnlyFileTypesAsDocuments() {
        assertThat(AttachmentFileType.TXT.getMediaKind()).isEqualTo(AttachmentMediaKind.DOCUMENT);
        assertThat(AttachmentFileType.LOG.getMediaKind()).isEqualTo(AttachmentMediaKind.DOCUMENT);
        assertThat(AttachmentFileType.PDF.getMediaKind()).isEqualTo(AttachmentMediaKind.DOCUMENT);
    }

    @Test
    void mapsDownloadOnlyFileTypesToContentTypes() {
        assertThat(AttachmentFileType.TXT.getContentType()).isEqualTo("text/plain");
        assertThat(AttachmentFileType.LOG.getContentType()).isEqualTo("text/plain");
        assertThat(AttachmentFileType.PDF.getContentType()).isEqualTo("application/pdf");
    }
}
