package ds.project.orino.planner.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * presigned 업로드 URL 발급 요청.
 *
 * @param contentType 업로드할 이미지 MIME 타입 (image/* 만 허용)
 */
public record ImageUploadUrlRequest(
        @NotBlank
        @Pattern(regexp = "image/(png|jpeg|jpg|gif|webp|svg\\+xml)",
                message = "지원하지 않는 이미지 형식입니다.")
        String contentType
) {
}
