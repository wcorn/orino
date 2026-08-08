package ds.project.orino.planner.travel.push.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 웹푸시 페이로드 암호화 — RFC 8291(Message Encryption) + RFC 8188(aes128gcm).
 *
 * <p>푸시 서비스는 <b>내용을 볼 수 없다</b>. 브라우저가 준 구독 키로 종단 암호화해서 보내고,
 * 복호화는 기기의 Service Worker가 한다. 그래서 이 계산이 어긋나면 푸시 서비스는 200을 주고도
 * 기기에는 아무것도 뜨지 않는다 — 조용히 실패하는 종류다.
 *
 * <p>필요한 것이 전부 JDK에 있어(ECDH · HMAC · AES-GCM) 외부 의존성을 쓰지 않는다.
 */
public final class WebPushEncryption {

    private static final byte[] AUTH_INFO = "WebPush: info\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CEK_INFO =
            "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NONCE_INFO =
            "Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8);

    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** 레코드 마지막임을 알리는 구분자(RFC 8188 §2). 한 레코드로만 보낸다. */
    private static final byte LAST_RECORD = 0x02;

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {
    }

    /**
     * @param uaPublicKey  구독의 {@code p256dh}(65바이트 비압축 점)
     * @param authSecret   구독의 {@code auth}(16바이트)
     * @param payload      보낼 평문
     * @param recordSize   레코드 크기. 한 레코드에 담기므로 페이로드보다 커야 한다
     */
    public static byte[] encrypt(byte[] uaPublicKey, byte[] authSecret,
                                 byte[] payload, int recordSize) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return encrypt(uaPublicKey, authSecret, payload, recordSize, salt,
                P256.generateKeyPair());
    }

    /**
     * 소금과 발신 키쌍을 밖에서 넣는 형태. 테스트가 결정적이어야 검증이 가능하다 —
     * 같은 입력에 같은 바이트가 나와야 다른 구현과 맞대어 볼 수 있다.
     */
    public static byte[] encrypt(byte[] uaPublicKey, byte[] authSecret, byte[] payload,
                                 int recordSize, byte[] salt, KeyPair senderKeyPair) {
        byte[] asPublic = P256.encode(senderKeyPair.getPublic());
        byte[] ikm = inputKeyingMaterial(
                senderKeyPair.getPrivate(), uaPublicKey, asPublic, authSecret);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hkdfExpand(prk, CEK_INFO), KEY_BYTES);
        byte[] nonce = Arrays.copyOf(hkdfExpand(prk, NONCE_INFO), NONCE_BYTES);

        byte[] ciphertext = aesGcm(cek, nonce, withDelimiter(payload));
        return record(salt, recordSize, asPublic, ciphertext);
    }

    /**
     * RFC 8291 §3.3 — 공유 비밀과 <b>양쪽 공개키</b>로 입력 키를 만든다.
     *
     * <p>{@code auth_secret}이 HKDF의 소금으로 들어가는 것이 핵심이다. 이게 없으면 공개키만
     * 아는 쪽도 키를 유도할 수 있다.
     */
    private static byte[] inputKeyingMaterial(PrivateKey asPrivate, byte[] uaPublic,
                                              byte[] asPublic, byte[] authSecret) {
        byte[] sharedSecret = ecdh(asPrivate, P256.publicKey(uaPublic));
        byte[] info = concat(AUTH_INFO, uaPublic, asPublic);
        return hkdfExpand(hmac(authSecret, sharedSecret), info);
    }

    private static byte[] ecdh(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(privateKey);
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret();
        } catch (Exception e) {
            throw new IllegalStateException("ECDH 계산에 실패했습니다.", e);
        }
    }

    /** HKDF-Expand, L ≤ 32라 한 번의 HMAC이면 된다(RFC 5869). */
    private static byte[] hkdfExpand(byte[] prk, byte[] info) {
        return hmac(prk, concat(info, new byte[] {0x01}));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산에 실패했습니다.", e);
        }
    }

    private static byte[] aesGcm(byte[] key, byte[] nonce, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new IllegalStateException("페이로드 암호화에 실패했습니다.", e);
        }
    }

    /** 평문 뒤에 레코드 구분자를 붙인다. 패딩은 쓰지 않는다. */
    private static byte[] withDelimiter(byte[] payload) {
        return concat(payload, new byte[] {LAST_RECORD});
    }

    /** RFC 8188 §2 헤더 — 소금 · 레코드 크기 · 키 식별자(발신 공개키) · 본문. */
    private static byte[] record(byte[] salt, int recordSize, byte[] keyId, byte[] ciphertext) {
        ByteBuffer buffer = ByteBuffer.allocate(
                SALT_BYTES + 4 + 1 + keyId.length + ciphertext.length);
        buffer.put(salt);
        buffer.putInt(recordSize);
        buffer.put((byte) keyId.length);
        buffer.put(keyId);
        buffer.put(ciphertext);
        return buffer.array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
