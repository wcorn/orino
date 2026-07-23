package ds.project.orino.planner.lifelog.image.service;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import ds.project.orino.planner.lifelog.image.dto.ImageKind;
import ds.project.orino.planner.lifelog.image.dto.LifelogImageUploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 일상기록 사진의 presigned 업로드 URL 발급과 오브젝트 삭제를 담당한다.
 * <p>
 * 업로드 바이트는 노트 이미지와 마찬가지로 BE를 거치지 않고 브라우저가 presigned URL로 MinIO에
 * 직접 PUT 한다(zero-copy). 원본/썸네일은 서로 다른 key prefix로 저장하고, 발급 응답에
 * {@code objectKey}를 함께 돌려줘 moment 저장 시 그대로 쓰게 한다.
 */
@Service
public class LifelogImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(LifelogImageStorageService.class);

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp");

    private static final String ORIGINAL_PREFIX = "lifelog/moments";
    private static final String THUMB_PREFIX = "lifelog/thumbs";

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final ImageStorageProperties props;

    public LifelogImageStorageService(S3Presigner imageS3Presigner,
                                      S3Client lifelogImageS3Client,
                                      ImageStorageProperties props) {
        this.presigner = imageS3Presigner;
        this.s3Client = lifelogImageS3Client;
        this.props = props;
    }

    /**
     * memberId·kind별 경로에 랜덤 키를 만들고 presigned PUT URL·공개 URL·objectKey를 반환한다.
     */
    public LifelogImageUploadUrlResponse createUploadUrl(Long memberId, String contentType, ImageKind kind) {
        String ext = EXTENSIONS.getOrDefault(contentType, "bin");
        String prefix = kind == ImageKind.THUMB ? THUMB_PREFIX : ORIGINAL_PREFIX;
        String key = "%s/%d/%s.%s".formatted(prefix, memberId, UUID.randomUUID(), ext);

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

        return new LifelogImageUploadUrlResponse(uploadUrl, publicUrl, key);
    }

    /**
     * 오브젝트를 best-effort로 삭제한다. DB 정합성이 우선이라 삭제 실패는 로그만 남기고 넘어간다
     * (고아 오브젝트는 용량만 차지 — 주기적 스윕으로 회수). null/blank 키는 건너뛴다.
     */
    public void deleteObjects(Collection<String> objectKeys) {
        if (objectKeys == null) {
            return;
        }
        for (String key : objectKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .build());
            } catch (RuntimeException e) {
                log.warn("일상기록 사진 삭제 실패(best-effort, 무시): key={}", key, e);
            }
        }
    }

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
