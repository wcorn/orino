package ds.project.orino.planner.travel.photo.dto;

/**
 * presigned 업로드 URL 발급 응답.
 *
 * @param uploadUrl 브라우저가 바이너리를 직접 PUT 할 presigned URL(만료 있음)
 * @param publicUrl 업로드 후 보여줄 공개 URL
 * @param objectKey 메타 등록(`POST /photos`)에 그대로 실어 보낼 key
 */
public record PhotoUploadUrlResponse(
        String uploadUrl,
        String publicUrl,
        String objectKey
) {
}
