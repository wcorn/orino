package ds.project.orino.planner.google.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Calendar API의 primary 캘린더 조회 응답 일부.
 * primary 캘린더의 {@code id}는 연동 계정 이메일과 같아 표시용 email로 재사용한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GooglePrimaryCalendar(String id, String summary) {
}
