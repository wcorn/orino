package ds.project.orino.planner.shortlink.dto;

/** 활성 ↔ 비활성 토글 결과. 만료된 링크를 켜도 EXPIRED로 보인다 — 만료가 상태를 이긴다. */
public record ToggleResponse(LinkState state) {
}
