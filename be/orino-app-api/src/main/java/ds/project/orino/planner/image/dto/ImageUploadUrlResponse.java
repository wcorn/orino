package ds.project.orino.planner.image.dto;

/**
 * presigned 업로드 URL 발급 응답.
 *
 * @param uploadUrl 브라우저가 이미지 바이너리를 PUT 할 presigned URL (만료 있음)
 * @param publicUrl 업로드 후 노트에 저장/표시할 공개 URL
 */
public record ImageUploadUrlResponse(
        String uploadUrl,
        String publicUrl
) {
}
