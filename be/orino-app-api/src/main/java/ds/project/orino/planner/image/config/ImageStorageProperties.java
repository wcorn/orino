package ds.project.orino.planner.image.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 노트 이미지 저장소(MinIO, S3 호환) 설정.
 *
 * @param endpoint            브라우저가 presigned URL로 접근할 공개 주소 (예: https://img.orino.dev)
 * @param bucket              버킷명 (note-images)
 * @param region              S3 region (MinIO는 무관, 서명용 임의값)
 * @param accessKey           MinIO access key
 * @param secretKey           MinIO secret key
 * @param presignExpirySeconds presigned URL 유효 시간(초)
 */
@ConfigurationProperties(prefix = "storage.image")
public record ImageStorageProperties(
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        long presignExpirySeconds
) {
}
