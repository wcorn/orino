package ds.project.orino.domain.planner.google.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Google refresh token 대칭키 암호화(AES-256-GCM). 키는 env 주입 시크릿을 SHA-256으로 32바이트화한다.
 *
 * <p>암호문 = base64(IV(12B) || ciphertext+tag). 매 암호화마다 IV가 달라 같은 평문도 다른 결과를 낸다(GCM 인증 포함).
 * 키가 바뀌면 기존 암호문을 복호화할 수 없으므로 운영에서 키를 안정적으로 유지해야 한다.
 */
@Component
public class RefreshTokenEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenEncryptor(
            @Value("${planner.google.refresh-token-key:default-dev-refresh-token-key-change-me}") String secret) {
        try {
            this.keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("refresh token 암호화 키 초기화 실패", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("refresh token 암호화 실패", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("refresh token 복호화 실패", e);
        }
    }

    /** 우리 형식으로 복호화 가능한 값(암호화됨)인지 — 평문 마이그레이션 판별용. */
    public boolean isEncrypted(String value) {
        if (value == null) {
            return false;
        }
        try {
            decrypt(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private SecretKeySpec key() {
        return new SecretKeySpec(keyBytes, "AES");
    }
}
