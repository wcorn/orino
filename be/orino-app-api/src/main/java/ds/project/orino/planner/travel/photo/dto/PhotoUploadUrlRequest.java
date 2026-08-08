package ds.project.orino.planner.travel.photo.dto;

import ds.project.orino.planner.lifelog.image.dto.ImageKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 여행 사진 presigned 업로드 URL 발급 요청.
 *
 * <p>{@code image/jpeg}만 받는다 — FE가 canvas로 재인코딩해 EXIF를 떨군 결과가 JPEG이다(§1.6).
 * 원본 형식을 그대로 허용하면 재인코딩을 건너뛴 파일이 위치정보를 달고 올라올 수 있다.
 *
 * @param kind 원본/썸네일. key prefix가 갈린다
 */
public record PhotoUploadUrlRequest(

        @NotBlank
        @Pattern(regexp = "image/jpeg", message = "지원하지 않는 이미지 형식입니다.")
        String contentType,

        @NotNull
        ImageKind kind
) {
}
