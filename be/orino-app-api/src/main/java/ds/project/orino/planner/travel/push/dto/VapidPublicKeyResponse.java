package ds.project.orino.planner.travel.push.dto;

/**
 * 브라우저가 구독할 때 쓰는 서버 공개키.
 *
 * <p>비밀이 아니라서 그냥 내려준다. FE에 박아두지 않는 이유는 <b>한 곳에서만 관리</b>하기
 * 위해서다 — 키를 갈면 서버 설정만 바꾸면 되고, FE 빌드를 다시 하지 않아도 된다.
 *
 * @param publicKey base64url. 키가 없으면 null이고 FE는 알림 UI를 감춘다
 */
public record VapidPublicKeyResponse(String publicKey) {
}
