package ds.project.orino.planner.travel.push.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 페이로드 암호화 검증.
 *
 * <p>이 계산은 <b>조용히 실패한다</b> — 어긋나면 푸시 서비스가 200을 주고도 기기엔 아무것도
 * 뜨지 않는다. 그래서 "돌아간다"가 아니라 <b>규격과 바이트가 같은가</b>로 확인한다.
 *
 * <p>입력은 RFC 8291 §5의 테스트 벡터고, 기대값은 널리 쓰이는 Node {@code http_ece}
 * (npm {@code web-push}의 암호화 엔진)로 같은 입력에서 뽑은 것이다. 우리 구현·규격·기존
 * 구현 셋이 한 점에서 만나야 통과한다.
 */
class WebPushEncryptionTest {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final byte[] UA_PUBLIC = DECODER.decode(
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
    private static final byte[] UA_PRIVATE = DECODER.decode(
            "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94");
    private static final byte[] AS_PUBLIC = DECODER.decode(
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
    private static final byte[] AS_PRIVATE = DECODER.decode(
            "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
    private static final byte[] AUTH_SECRET = DECODER.decode("BTBZMqHH6r4Tts7J_aSIgg");
    private static final byte[] SALT = DECODER.decode("DGv6ra1nlYgDCS1FRnbzlw");

    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String EXPECTED =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLoc"
                    + "InmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXL"
                    + "WyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    private static KeyPair senderKeyPair() {
        return new KeyPair(P256.publicKey(AS_PUBLIC), P256.privateKey(AS_PRIVATE));
    }

    @Test
    @DisplayName("RFC 8291 테스트 벡터와 바이트가 같다")
    void matchesReferenceVector() {
        byte[] encrypted = WebPushEncryption.encrypt(
                UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                4096, SALT, senderKeyPair());

        assertThat(ENCODER.encodeToString(encrypted)).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("헤더에 소금·레코드 크기·발신 공개키가 그대로 실린다")
    void carriesHeader() {
        byte[] encrypted = WebPushEncryption.encrypt(
                UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                4096, SALT, senderKeyPair());

        // salt(16) + rs(4) + idlen(1) + key(65)
        assertThat(java.util.Arrays.copyOf(encrypted, 16)).isEqualTo(SALT);
        assertThat(encrypted[20]).isEqualTo((byte) 65);
        assertThat(java.util.Arrays.copyOfRange(encrypted, 21, 86)).isEqualTo(AS_PUBLIC);
    }

    @Test
    @DisplayName("소금이 매번 달라 같은 내용도 다른 바이트가 된다")
    void saltIsRandomPerMessage() {
        byte[] first = WebPushEncryption.encrypt(
                UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8), 4096);
        byte[] second = WebPushEncryption.encrypt(
                UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8), 4096);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("구독의 auth가 다르면 결과가 달라진다 — 공개키만으로는 못 푼다")
    void authSecretChangesResult() {
        byte[] otherAuth = DECODER.decode("AAAAAAAAAAAAAAAAAAAAAA");

        byte[] withReal = WebPushEncryption.encrypt(UA_PUBLIC, AUTH_SECRET,
                PLAINTEXT.getBytes(StandardCharsets.UTF_8), 4096, SALT, senderKeyPair());
        byte[] withOther = WebPushEncryption.encrypt(UA_PUBLIC, otherAuth,
                PLAINTEXT.getBytes(StandardCharsets.UTF_8), 4096, SALT, senderKeyPair());

        assertThat(withReal).isNotEqualTo(withOther);
    }

    @Test
    @DisplayName("P-256 키를 원시 바이트로 왕복해도 그대로다")
    void encodesKeysRoundTrip() {
        assertThat(P256.encode(P256.publicKey(UA_PUBLIC))).isEqualTo(UA_PUBLIC);
        assertThat(P256.encode(P256.privateKey(UA_PRIVATE))).isEqualTo(UA_PRIVATE);
    }
}
