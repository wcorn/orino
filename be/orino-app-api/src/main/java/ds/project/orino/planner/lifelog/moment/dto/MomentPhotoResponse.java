package ds.project.orino.planner.lifelog.moment.dto;

/**
 * 응답용 사진. key 대신 조립된 공개 URL을 준다.
 */
public record MomentPhotoResponse(
        Long id,
        String url,
        String thumbUrl,
        Integer width,
        Integer height,
        Integer sortOrder
) {
}
