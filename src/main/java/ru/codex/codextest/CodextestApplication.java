package ru.codex.codextest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.codex.codextest.service.VideoPreviewWorker;
import java.util.Arrays;

@SpringBootApplication
public class CodextestApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "--video-preview-worker".equals(args[0])) {
            VideoPreviewWorker.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(CodextestApplication.class, args);
    }

}
