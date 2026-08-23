package ds.project.orino.planner.shortlink.redirect;

import java.util.Locale;

/**
 * {@code Location} 헤더 조립(명세 §6.4).
 *
 * <p>규칙은 셋이고, 셋째가 이 모듈에서 가장 조용히 깨지는 부분이다.
 * <ol>
 *   <li>방문 쿼리가 없으면 목적지를 <b>그대로</b> 쓴다 — {@code ?}를 새로 붙이지 않는다</li>
 *   <li>방문 쿼리가 있으면 목적지 쿼리 뒤에 {@code ?} 또는 {@code &}로 이어붙인다</li>
 *   <li><b>목적지가 서명된 URL이면 방문 쿼리를 버린다</b>(결정 기록 D-13)</li>
 * </ol>
 *
 * <p>셋째가 필요한 이유가 이 모듈의 출발점과 같다. 가장 자주 단축할 대상이 MinIO presigned
 * URL이고, 거기에 파라미터가 하나만 더 붙어도 목적지가 {@code SignatureDoesNotMatch}를 뱉는다.
 * <b>드문 쿼리 전달을 포기하는 편이, 가장 중요한 유스케이스가 조용히 깨지는 것보다 낫다.</b>
 */
public final class TargetUrlAssembler {

    /** 이 이름의 파라미터가 목적지에 있으면 무엇을 덧붙여도 서명 검증이 깨진다. */
    private static final String[] SIGNATURE_PARAMS = {
            "x-amz-signature", "x-goog-signature", "signature"
    };

    private TargetUrlAssembler() {
    }

    /**
     * @param targetUrl   저장된 목적지
     * @param visitQuery  방문 요청의 쿼리스트링(없으면 null·빈 문자열)
     */
    public static String assemble(String targetUrl, String visitQuery) {
        if (visitQuery == null || visitQuery.isBlank() || hasSignature(targetUrl)) {
            return targetUrl;
        }
        // 프래그먼트는 서버에 오지 않지만 목적지에는 있을 수 있다. 쿼리는 그 앞에 붙어야 한다 —
        // 뒤에 붙이면 프래그먼트의 일부가 되어 목적지 서버가 영영 보지 못한다.
        int hash = targetUrl.indexOf('#');
        String base = hash < 0 ? targetUrl : targetUrl.substring(0, hash);
        String fragment = hash < 0 ? "" : targetUrl.substring(hash);
        String separator = base.indexOf('?') < 0 ? "?" : "&";
        return base + separator + visitQuery + fragment;
    }

    /** 목적지 <b>쿼리의 파라미터 이름</b>만 본다 — 경로에 우연히 들어간 문자열에 속지 않게. */
    private static boolean hasSignature(String targetUrl) {
        int start = targetUrl.indexOf('?');
        if (start < 0) {
            return false;
        }
        int end = targetUrl.indexOf('#', start);
        String query = end < 0 ? targetUrl.substring(start + 1) : targetUrl.substring(start + 1, end);
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String name = (equals < 0 ? pair : pair.substring(0, equals)).toLowerCase(Locale.ROOT);
            for (String signature : SIGNATURE_PARAMS) {
                if (name.equals(signature)) {
                    return true;
                }
            }
        }
        return false;
    }
}
