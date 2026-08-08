package ds.project.orino.planner.travel.photo;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import ds.project.orino.planner.lifelog.image.dto.ImageKind;
import ds.project.orino.planner.travel.photo.dto.PhotoUploadUrlResponse;
import ds.project.orino.planner.travel.photo.service.TravelPhotoStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * key 조립 규칙과 삭제의 best-effort 계약을 고정한다. presigned 서명은 순수 로컬 계산이라
 * 네트워크 없이 검증할 수 있다.
 */
class TravelPhotoStorageServiceTest {

    private final ImageStorageProperties props = new ImageStorageProperties(
            "https://img.orino.dev", "note-images", "us-east-1", "k", "s", 300);

    private S3Presigner presigner() {
        return S3Presigner.builder()
                .region(Region.of(props.region()))
                .endpointOverride(URI.create(props.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .build();
    }

    @Test
    @DisplayName("원본과 썸네일은 다른 prefix로 갈린다 — 나중에 여행 사진만 골라 다룰 수 있어야 한다")
    void separatesPrefixesByKind() {
        TravelPhotoStorageService service =
                new TravelPhotoStorageService(presigner(), mock(S3Client.class), props);

        PhotoUploadUrlResponse original =
                service.createUploadUrl(91L, "image/jpeg", ImageKind.ORIGINAL);
        PhotoUploadUrlResponse thumb =
                service.createUploadUrl(91L, "image/jpeg", ImageKind.THUMB);

        assertThat(original.objectKey()).startsWith("travel/activities/91/").endsWith(".jpg");
        assertThat(thumb.objectKey()).startsWith("travel/thumbs/91/").endsWith(".jpg");
        assertThat(original.uploadUrl()).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("공개 URL은 설정의 호스트로 조립한다 — 컬럼에 호스트를 박지 않는다")
    void buildsPublicUrlFromConfig() {
        TravelPhotoStorageService service =
                new TravelPhotoStorageService(presigner(), mock(S3Client.class), props);

        assertThat(service.toPublicUrl("travel/activities/91/a.jpg"))
                .isEqualTo("https://img.orino.dev/note-images/travel/activities/91/a.jpg");
        // 썸네일이 없는 사진이 있다 — null이 그대로 전달돼도 터지지 않아야 한다.
        assertThat(service.toPublicUrl(null)).isNull();
        assertThat(service.toPublicUrl("  ")).isNull();
    }

    @Test
    @DisplayName("삭제는 유효한 키만 시도한다")
    void deletesOnlyValidKeys() {
        S3Client s3 = mock(S3Client.class);
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        TravelPhotoStorageService service =
                new TravelPhotoStorageService(presigner(), s3, props);

        service.deleteObjects(Arrays.asList("travel/activities/1/a.jpg", null, "  "));

        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("삭제 실패를 삼킨다 — 오브젝트가 안 지워졌다고 지운 사진이 되살아나면 안 된다")
    void swallowsDeleteFailures() {
        S3Client s3 = mock(S3Client.class);
        doThrow(AwsServiceException.builder().message("boom").build())
                .when(s3).deleteObject(any(DeleteObjectRequest.class));
        TravelPhotoStorageService service =
                new TravelPhotoStorageService(presigner(), s3, props);

        assertThatCode(() -> service.deleteObjects(List.of("a.jpg", "b.jpg")))
                .doesNotThrowAnyException();
        verify(s3, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }
}
