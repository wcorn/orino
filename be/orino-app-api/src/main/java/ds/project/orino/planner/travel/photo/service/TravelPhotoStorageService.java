package ds.project.orino.planner.travel.photo.service;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import ds.project.orino.planner.lifelog.image.dto.ImageKind;
import ds.project.orino.planner.travel.photo.dto.PhotoUploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 여행 사진의 presigned 업로드 URL 발급과 오브젝트 삭제.
 *
 * <p>업로드 바이트는 <b>BE를 거치지 않는다</b> — 브라우저가 presigned URL로 MinIO에 직접 PUT
 * 한다. 사진 열 장이 서버 힙을 지나가지 않아야 한다.
 *
 * <p>MinIO·버킷·자격증명은 노트/일상기록 이미지와 공유한다({@link ImageStorageProperties}).
 * 나뉘는 것은 <b>key prefix</b>뿐이라, 나중에 여행 사진만 골라 지우거나 옮길 수 있다.
 */
@Service
public class TravelPhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(TravelPhotoStorageService.class);

    private static final String ORIGINAL_PREFIX = "travel/activities";
    private static final String THUMB_PREFIX = "travel/thumbs";

    /** 구글에서 받아 캐시한 장소 대표 사진. 사용자가 올린 사진과 섞이지 않게 나눈다. */
    private static final String PLACE_PREFIX = "travel/places";

    /** FE가 canvas로 재인코딩해 올리므로 항상 JPEG이다(§1.6 EXIF 제거). */
    private static final String EXTENSION = "jpg";

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final ImageStorageProperties props;

    public TravelPhotoStorageService(S3Presigner imageS3Presigner,
                                     S3Client lifelogImageS3Client,
                                     ImageStorageProperties props) {
        this.presigner = imageS3Presigner;
        this.s3Client = lifelogImageS3Client;
        this.props = props;
    }

    /**
     * 일정별 경로에 랜덤 키를 만들고 presigned PUT URL을 발급한다.
     *
     * <p>키에 {@code activityId}를 넣는 것은 나중에 사람이 버킷을 들여다볼 때 어느 일정의
     * 사진인지 알기 위한 것이다. 접근 제어는 여기가 아니라 API 소유권 검사가 한다.
     */
    public PhotoUploadUrlResponse createUploadUrl(Long activityId, String contentType,
                                                  ImageKind kind) {
        String prefix = kind == ImageKind.THUMB ? THUMB_PREFIX : ORIGINAL_PREFIX;
        String key = "%s/%d/%s.%s".formatted(prefix, activityId, UUID.randomUUID(), EXTENSION);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.presignExpirySeconds()))
                .putObjectRequest(objectRequest)
                .build();

        return new PhotoUploadUrlResponse(
                presigner.presignPutObject(presignRequest).url().toString(),
                toPublicUrl(key), key);
    }

    /**
     * 구글 장소 사진을 서버가 직접 올린다.
     *
     * <p>사용자 사진과 달리 <b>바이트가 서버를 지나간다</b> — 브라우저가 구글에서 받아 올 수
     * 없기 때문이다(키가 필요하고 URL이 만료된다). 대신 장소당 한 장, 800px이라 작다.
     *
     * @return 저장된 object key. 실패하면 비어 있다
     */
    public Optional<String> uploadPlacePhoto(Long placeId, byte[] bytes) {
        String key = "%s/%d/%s.jpg".formatted(PLACE_PREFIX, placeId, UUID.randomUUID());
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
                            .contentType("image/jpeg")
                            .build(),
                    RequestBody.fromBytes(bytes));
            return Optional.of(key);
        } catch (RuntimeException e) {
            // 사진이 없다고 장소를 못 쓰게 만들지 않는다 — 다음 갱신 때 다시 시도한다.
            log.warn("장소 사진 업로드 실패(무시): placeId={}, {}", placeId, e.getMessage());
            return Optional.empty();
        }
    }

    /** object key를 공개 URL로 조립한다. 호스트는 설정에서 온다 — 환경별로 갈린다. */
    public String toPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return "%s/%s/%s".formatted(stripTrailingSlash(props.endpoint()), props.bucket(), objectKey);
    }

    /**
     * 오브젝트를 best-effort로 지운다. <b>DB 정합성이 우선이다</b> — 삭제 실패로 트랜잭션을
     * 되돌리면 화면에서 지운 사진이 되살아난다. 남은 오브젝트는 용량만 차지한다.
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
                log.warn("여행 사진 삭제 실패(best-effort, 무시): key={}", key, e);
            }
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
