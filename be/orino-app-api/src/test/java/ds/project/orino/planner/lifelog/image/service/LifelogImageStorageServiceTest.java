package ds.project.orino.planner.lifelog.image.service;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 삭제 유틸의 best-effort 계약을 고정한다: null/blank 키는 건너뛰고, 개별 삭제 실패는 삼켜
 * DB 정합성 흐름을 막지 않는다. presigned URL 발급 경로는 컨트롤러 통합 테스트에서 검증한다.
 */
class LifelogImageStorageServiceTest {

    private final ImageStorageProperties props = new ImageStorageProperties(
            "https://img.orino.dev", "note-images", "us-east-1", "k", "s", 300);

    @Test
    @DisplayName("deleteObjects - 유효한 키만 삭제하고 null/blank는 건너뛴다")
    void deletesOnlyValidKeys() {
        S3Client s3 = mock(S3Client.class);
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        LifelogImageStorageService service = new LifelogImageStorageService(null, s3, props);

        service.deleteObjects(Arrays.asList("lifelog/moments/1/a.jpg", null, "  ", "lifelog/thumbs/1/a.jpg"));

        verify(s3, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("deleteObjects - 개별 삭제 실패는 삼키고 나머지를 계속 지운다")
    void swallowsFailuresBestEffort() {
        S3Client s3 = mock(S3Client.class);
        doThrow(AwsServiceException.builder().message("boom").build())
                .when(s3).deleteObject(any(DeleteObjectRequest.class));
        LifelogImageStorageService service = new LifelogImageStorageService(null, s3, props);

        assertThatCode(() -> service.deleteObjects(List.of("k1.jpg", "k2.jpg")))
                .doesNotThrowAnyException();
        verify(s3, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("deleteObjects - null 컬렉션은 아무것도 안 한다")
    void nullCollectionIsNoop() {
        S3Client s3 = mock(S3Client.class);
        LifelogImageStorageService service = new LifelogImageStorageService(null, s3, props);

        service.deleteObjects(null);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
