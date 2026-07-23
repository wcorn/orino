package ds.project.orino.planner.lifelog.moment.dto;

import ds.project.orino.domain.planner.lifelog.entity.Mood;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 기록 생성·수정 공통 요청. 수정(PUT)은 사진·태그를 이 배열로 <b>전체 치환</b>한다.
 *
 * @param occurredAt 발생시각(없으면 서버 현재시각)
 * @param body       본문
 * @param mood       기분
 * @param lat        위도 (lng와 함께 오거나 함께 없음)
 * @param lng        경도
 * @param placeName  장소명(FE가 역지오코딩 결과를 전달)
 * @param tags       태그 목록(중복·공백은 서버가 정리)
 * @param photos     사진 목록(순서 포함)
 */
public record MomentWriteRequest(
        Instant occurredAt,
        String body,
        Mood mood,
        BigDecimal lat,
        BigDecimal lng,
        String placeName,
        List<String> tags,
        @Valid
        List<MomentPhotoRequest> photos
) {
}
