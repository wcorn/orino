package ds.project.orino.planner.shortlink.dto;

/**
 * 커스텀 슬러그 중복 검사. <b>삭제된 링크의 슬러그도 사용 중이다</b>(명세 §3.1) —
 * 왜 막혔는지는 알려주지 않는다.
 */
public record SlugAvailableResponse(boolean available) {
}
