package ds.project.orino.planner.lifelog.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 일상기록 사진 presigned 업로드 URL 발급 요청.
 *
 * @param contentType 업로드할 이미지 MIME 타입 (image/* 만 허용)
 * @param kind        원본/썸네일 구분 — key prefix가 갈린다
 */
public record LifelogImageUploadUrlRequest(
        @NotBlank
        @Pattern(regexp = "image/(png|jpeg|jpg|gif|webp)",
                message = "지원하지 않는 이미지 형식입니다.")
        String contentType,

        @NotNull
        ImageKind kind
) {
}
