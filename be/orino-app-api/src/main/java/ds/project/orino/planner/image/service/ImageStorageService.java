package ds.project.orino.planner.image.service;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import ds.project.orino.planner.image.dto.ImageUploadUrlResponse;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 노트 이미지 업로드용 presigned URL을 발급한다.
 * <p>
 * 바이너리는 BE를 거치지 않고 브라우저가 presigned URL로 MinIO에 직접 PUT 한다.
 */
@Service
public class ImageStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final S3Presigner presigner;
    private final ImageStorageProperties props;

    public ImageStorageService(S3Presigner imageS3Presigner, ImageStorageProperties props) {
        this.presigner = imageS3Presigner;
        this.props = props;
    }

    /**
     * memberId별 경로에 랜덤 키를 생성하고 presigned PUT URL과 공개 URL을 반환한다.
     */
    public ImageUploadUrlResponse createUploadUrl(Long memberId, String contentType) {
        String ext = EXTENSIONS.getOrDefault(contentType, "bin");
        String key = "%d/%s.%s".formatted(memberId, UUID.randomUUID(), ext);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.presignExpirySeconds()))
                .putObjectRequest(objectRequest)
                .build();

        String uploadUrl = presigner.presignPutObject(presignRequest).url().toString();
        String publicUrl = "%s/%s/%s".formatted(
                stripTrailingSlash(props.endpoint()), props.bucket(), key);

        return new ImageUploadUrlResponse(uploadUrl, publicUrl);
    }

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
