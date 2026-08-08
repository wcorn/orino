package ds.project.orino.planner.travel.photo.dto;

import ds.project.orino.domain.planner.travel.entity.TripActivityPhoto;

/**
 * 사진 한 장. <b>key가 아니라 URL을 내려준다</b> — 호스트 조립 규칙이 화면마다 흩어지면
 * 환경이 바뀔 때 한 곳만 고쳐도 나머지가 깨진다.
 *
 * @param thumbUrl 썸네일이 없으면 null. 화면이 원본을 줄여 쓴다
 */
public record PhotoResponse(
        Long id,
        String url,
        String thumbUrl,
        Integer width,
        Integer height
) {

    public static PhotoResponse of(TripActivityPhoto photo, String url, String thumbUrl) {
        return new PhotoResponse(photo.getId(), url, thumbUrl,
                photo.getWidth(), photo.getHeight());
    }
}
