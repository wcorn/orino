package ds.project.orino.planner.travel.trip.dto;

/**
 * 기간을 줄였을 때 보관함으로 밀려날 일정 수. 확인 모달의 "일정 {n}개가 미배정 보관함으로
 * 이동합니다."에 그대로 들어간다.
 *
 * <p>기간 단축을 확인 없이 시도했을 때의 409 응답에도 같은 형태로 실린다 — 클라이언트가
 * 미리보기를 건너뛰었더라도 같은 숫자를 보고 다시 물어볼 수 있어야 한다.
 */
public record ShrinkPreviewResponse(long movedActivityCount) {
}
