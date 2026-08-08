package ds.project.orino.planner.travel.push.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VAPID 서명 검증.
 *
 * <p>서명은 <b>스스로 만든 것을 스스로 확인</b>해도 의미가 없다 — 형식이 틀려도 양쪽이 같이
 * 틀리기 때문이다. 그래서 JDK 검증기로 원문을 다시 세워 확인하고, JWS가 요구하는
 * <b>고정 길이 R‖S</b>인지를 따로 못박는다(RFC 7518 §3.4).
 */
class VapidSignerTest {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String ENDPOINT =
            "https://fcm.googleapis.com/fcm/send/abcdef:APA91bF_someTokenValue";
    private static final String SUBJECT = "mailto:dsk08208@gmail.com";
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    private final KeyPair keyPair = P256.generateKeyPair();

    private String[] parts(String jwt) {
        return jwt.split("\\.");
    }

    private String decode(String segment) {
        return new String(DECODER.decode(segment), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("JWT")
    class Jwt {

        @Test
        @DisplayName("헤더는 ES256이다")
        void headerIsEs256() {
            String jwt = VapidSigner.jwt("https://fcm.googleapis.com", SUBJECT,
                    keyPair.getPrivate(), NOW);

            assertThat(decode(parts(jwt)[0])).isEqualTo("""
                    {"typ":"JWT","alg":"ES256"}""");
        }

        @Test
        @DisplayName("aud는 엔드포인트 전체가 아니라 출처만이다")
        void audienceIsOriginOnly() {
            // 엔드포인트 전체를 넣으면 구독마다 aud가 달라져 푸시 서비스가 거부한다.
            assertThat(VapidSigner.audience(ENDPOINT)).isEqualTo("https://fcm.googleapis.com");
        }

        @Test
        @DisplayName("만료는 24시간을 넘지 않는다 (RFC 8292)")
        void expiryWithinLimit() {
            String jwt = VapidSigner.jwt("https://fcm.googleapis.com", SUBJECT,
                    keyPair.getPrivate(), NOW);
            String claims = decode(parts(jwt)[1]);

            long exp = Long.parseLong(claims.replaceAll(".*\"exp\":(\\d+).*", "$1"));
            assertThat(exp - NOW.getEpochSecond()).isPositive()
                    .isLessThanOrEqualTo(24 * 60 * 60);
        }

        @Test
        @DisplayName("서명이 원문과 맞는다 — 검증기로 다시 세워 확인한다")
        void signatureVerifies() throws Exception {
            String jwt = VapidSigner.jwt("https://fcm.googleapis.com", SUBJECT,
                    keyPair.getPrivate(), NOW);
            String[] segments = parts(jwt);
            String signingInput = segments[0] + "." + segments[1];

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));

            assertThat(verifier.verify(joseToDer(DECODER.decode(segments[2])))).isTrue();
        }

        @Test
        @DisplayName("서명은 항상 64바이트다 — DER을 그대로 쓰면 길이가 흔들려 401이 난다")
        void signatureIsFixedLength() {
            // 자바로 VAPID를 붙일 때 가장 흔한 함정이라 여러 번 돌려 길이를 못박는다.
            for (int i = 0; i < 50; i++) {
                String jwt = VapidSigner.jwt("https://fcm.googleapis.com", SUBJECT,
                        P256.generateKeyPair().getPrivate(), NOW);

                assertThat(DECODER.decode(parts(jwt)[2])).hasSize(64);
            }
        }
    }

    @Nested
    @DisplayName("Authorization 헤더")
    class Header {

        @Test
        @DisplayName("vapid 스킴에 토큰과 공개키를 함께 싣는다")
        void carriesTokenAndKey() {
            String header = VapidSigner.authorizationHeader(ENDPOINT, SUBJECT,
                    P256.encode(keyPair.getPublic()), keyPair.getPrivate(), NOW);

            assertThat(header).startsWith("vapid t=").contains(", k=");
            String key = header.substring(header.indexOf(", k=") + 4);
            // 푸시 서비스가 이 키로 서명을 검증한다 — 구독 때 쓴 것과 같아야 한다.
            assertThat(DECODER.decode(key)).isEqualTo(P256.encode(keyPair.getPublic()));
        }
    }

    /** 검증기에 넣으려면 JOSE 형식을 DER로 되돌려야 한다. */
    private static byte[] joseToDer(byte[] jose) {
        byte[] r = trim(java.util.Arrays.copyOfRange(jose, 0, 32));
        byte[] s = trim(java.util.Arrays.copyOfRange(jose, 32, 64));
        int length = 2 + r.length + 2 + s.length;

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(0x30);
        out.write(length);
        out.write(0x02);
        out.write(r.length);
        out.writeBytes(r);
        out.write(0x02);
        out.write(s.length);
        out.writeBytes(s);
        return out.toByteArray();
    }

    /** DER INTEGER는 앞의 0을 빼고, 최상위 비트가 1이면 0x00을 붙인다. */
    private static byte[] trim(byte[] value) {
        int start = 0;
        while (start < value.length - 1 && value[start] == 0) {
            start++;
        }
        byte[] trimmed = java.util.Arrays.copyOfRange(value, start, value.length);
        if ((trimmed[0] & 0x80) != 0) {
            byte[] padded = new byte[trimmed.length + 1];
            System.arraycopy(trimmed, 0, padded, 1, trimmed.length);
            return padded;
        }
        return trimmed;
    }
}
