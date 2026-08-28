package ds.project.orino.planner.ledger.receipt;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 영수증 오브젝트의 presigned 업로드 URL 발급.
 *
 * <p><b>일상기록과 같은 버킷·같은 presigner를 쓴다</b> — prefix만 다르다. 새 저장소를 만들면
 * 백업·보존·용량을 볼 곳이 하나 더 늘고, 그 값이 영수증 몇 장에 값하지 않는다.
 *
 * <p>바이트는 BE를 거치지 않는다. 브라우저가 presigned URL로 MinIO에 직접 PUT 하고,
 * 서버에는 <b>키만</b> 돌아온다.
 *
 * <p><b>지우는 메서드가 없다.</b> 거래를 소프트 삭제해도 오브젝트는 남아야 하고(되돌리기),
 * 첨부를 떼어내도 마찬가지다. 고아 오브젝트 회수는 보존 배치의 몫이다 —
 * 지우는 길을 여기 열어 두면 언젠가 되돌릴 수 없는 삭제가 섞인다.
 */
@Service
public class LedgerReceiptStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/heic", "heic",
            "application/pdf", "pdf");

    private static final String PREFIX = "ledger/receipts";

    private final S3Presigner presigner;
    private final ImageStorageProperties props;

    public LedgerReceiptStorageService(S3Presigner imageS3Presigner,
                                       ImageStorageProperties props) {
        this.presigner = imageS3Presigner;
        this.props = props;
    }

    public LedgerReceiptDtos.UploadUrl createUploadUrl(Long memberId, String contentType) {
        String ext = EXTENSIONS.getOrDefault(contentType, "bin");
        String key = "%s/%d/%s.%s".formatted(PREFIX, memberId, UUID.randomUUID(), ext);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.presignExpirySeconds()))
                .putObjectRequest(objectRequest)
                .build();

        return new LedgerReceiptDtos.UploadUrl(
                presigner.presignPutObject(presignRequest).url().toString(),
                toPublicUrl(key),
                key);
    }

    /** 오브젝트 키를 공개 URL로 조립한다. 호스트를 하드코딩하지 않고 설정에서 받는다. */
    public String toPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return objectKey;
        }
        String endpoint = props.endpoint().endsWith("/")
                ? props.endpoint().substring(0, props.endpoint().length() - 1)
                : props.endpoint();
        return "%s/%s/%s".formatted(endpoint, props.bucket(), objectKey);
    }
}
