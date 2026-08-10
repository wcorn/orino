package ds.project.orino.planner.travel.trip.dto;

/**
 * 기간을 줄였을 때 무엇이 밀려나는지. 확인 모달의 "잘리는 날짜의 일정 4개가 미배정 보관함으로
 * 이동하고, 걸쳐 있던 숙소는 기간이 줄어듭니다."에 그대로 들어간다.
 *
 * <p>기간 단축을 확인 없이 시도했을 때의 409 응답에도 같은 형태로 실린다 — 클라이언트가
 * 미리보기를 건너뛰었더라도 같은 숫자를 보고 다시 물어볼 수 있어야 한다.
 *
 * @param movedActivityCount 보관함으로 옮겨질 일정 수
 * @param shrunkStayCount    체크아웃일이 당겨질 숙소 수 (v2.1)
 * @param removedStayCount   묵는 밤이 없어져 지워질 숙소 수 (v2.1)
 */
public record ShrinkPreviewResponse(
        long movedActivityCount,
        long shrunkStayCount,
        long removedStayCount
) {
}
