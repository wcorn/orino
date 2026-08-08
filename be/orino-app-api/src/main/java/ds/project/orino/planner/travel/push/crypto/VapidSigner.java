package ds.project.orino.planner.travel.push.crypto;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * VAPID 인증 헤더 — RFC 8292.
 *
 * <p>푸시 서비스에 "이 발송자가 그 구독을 만든 서버가 맞다"를 증명한다. ES256으로 서명한 JWT를
 * 헤더에 싣는다.
 *
 * <p>JDK의 ECDSA 서명은 <b>DER</b>로 나오는데 JWS는 <b>고정 길이 R‖S</b>를 요구한다(RFC 7518
 * §3.4). 이 변환을 빼먹으면 푸시 서비스가 401을 준다 — 자바로 VAPID를 붙일 때 가장 흔한 함정이다.
 */
public final class VapidSigner {

    /** 헤더·클레임은 고정이라 상수로 둔다. */
    private static final String HEADER = """
            {"typ":"JWT","alg":"ES256"}""";

    /** RFC 8292는 24시간을 넘지 말라고 한다. 여유를 두고 12시간. */
    private static final Duration EXPIRY = Duration.ofHours(12);

    private static final int COORDINATE_BYTES = 32;
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private VapidSigner() {
    }

    /**
     * @param endpoint 구독 엔드포인트. {@code aud}는 그 <b>출처(origin)</b>만 쓴다
     * @param subject  연락처({@code mailto:} 또는 https URL)
     */
    public static String authorizationHeader(String endpoint, String subject,
                                             byte[] publicKey, PrivateKey privateKey,
                                             Instant now) {
        String jwt = jwt(audience(endpoint), subject, privateKey, now);
        return "vapid t=%s, k=%s".formatted(jwt, BASE64URL.encodeToString(publicKey));
    }

    /** 엔드포인트 전체를 넣으면 안 된다 — 구독마다 aud가 달라져 캐시도 검증도 어긋난다. */
    static String audience(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    static String jwt(String audience, String subject, PrivateKey privateKey, Instant now) {
        String claims = """
                {"aud":"%s","exp":%d,"sub":"%s"}"""
                .formatted(audience, now.plus(EXPIRY).getEpochSecond(), subject);

        String signingInput = encode(HEADER) + "." + encode(claims);
        byte[] signature = sign(signingInput.getBytes(StandardCharsets.UTF_8), privateKey);
        return signingInput + "." + BASE64URL.encodeToString(signature);
    }

    private static String encode(String json) {
        return BASE64URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(data);
            return derToJose(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("VAPID 서명에 실패했습니다.", e);
        }
    }

    /**
     * DER {@code SEQUENCE { INTEGER r, INTEGER s }} → 64바이트 {@code R‖S}.
     *
     * <p>DER INTEGER는 최상위 비트가 1이면 앞에 0x00을 붙이고, 앞의 0은 생략한다. 그대로 이어
     * 붙이면 길이가 63~65로 흔들려 서명이 깨진다.
     */
    static byte[] derToJose(byte[] der) {
        int offset = 3;
        if (der[1] == (byte) 0x81) {
            offset = 4;
        }
        int rLength = der[offset];
        int sOffset = offset + rLength + 2;
        int sLength = der[sOffset];

        BigInteger r = new BigInteger(1, der, offset + 1, rLength);
        BigInteger s = new BigInteger(1, der, sOffset + 1, sLength);

        byte[] jose = new byte[COORDINATE_BYTES * 2];
        writeFixed(r, jose, 0);
        writeFixed(s, jose, COORDINATE_BYTES);
        return jose;
    }

    private static void writeFixed(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, COORDINATE_BYTES);
        System.arraycopy(bytes, bytes.length - length, target,
                offset + COORDINATE_BYTES - length, length);
    }
}
