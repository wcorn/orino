package ds.project.orino.planner.lifelog.moment.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 기록에 붙일 사진 하나. objectKey/thumbKey는 사전 발급된 presigned 업로드로 이미 MinIO에 올라간
 * 오브젝트를 가리킨다(#951). EXIF는 FE가 읽어 함께 보낸다.
 *
 * @param objectKey    MinIO 원본 key (필수)
 * @param thumbKey     썸네일 key
 * @param width        원본 너비(px)
 * @param height       원본 높이(px)
 * @param exifTakenAt  EXIF 촬영시각
 * @param exifLat      EXIF GPS 위도
 * @param exifLng      EXIF GPS 경도
 * @param sortOrder    기록 내 사진 순서(없으면 0)
 */
public record MomentPhotoRequest(
        @NotBlank
        String objectKey,
        String thumbKey,
        Integer width,
        Integer height,
        Instant exifTakenAt,
        BigDecimal exifLat,
        BigDecimal exifLng,
        Integer sortOrder
) {
}
