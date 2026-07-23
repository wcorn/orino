package ds.project.orino.planner.lifelog.image.dto;

/**
 * 업로드할 이미지 종류. 원본과 썸네일을 각각 다른 key prefix로 저장한다.
 *
 * <ul>
 *     <li>{@link #ORIGINAL} — 원본. {@code lifelog/moments/...}</li>
 *     <li>{@link #THUMB} — 썸네일(FE canvas 생성). {@code lifelog/thumbs/...}</li>
 * </ul>
 */
public enum ImageKind {
    ORIGINAL,
    THUMB
}
