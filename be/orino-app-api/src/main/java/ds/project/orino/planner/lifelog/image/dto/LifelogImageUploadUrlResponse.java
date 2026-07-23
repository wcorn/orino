package ds.project.orino.planner.lifelog.image.dto;

/**
 * 일상기록 사진 presigned 업로드 URL 발급 응답.
 *
 * @param uploadUrl 브라우저가 이미지 바이너리를 직접 PUT 할 presigned URL (만료 있음)
 * @param publicUrl 업로드 후 표시할 공개 URL (img.orino.dev)
 * @param objectKey MinIO object key — moment 생성 시 {@code objectKey}/{@code thumbKey}로 저장한다
 */
public record LifelogImageUploadUrlResponse(
        String uploadUrl,
        String publicUrl,
        String objectKey
) {
}
