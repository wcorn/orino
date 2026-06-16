package ds.project.orino.domain.planner.google.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenEncryptorTest {

    private final RefreshTokenEncryptor encryptor =
            new RefreshTokenEncryptor("test-secret-key");

    @Test
    @DisplayName("암호화→복호화 라운드트립이 원문을 복원한다")
    void roundTrip() {
        String plaintext = "1//0gWj3-google-refresh-token-example";

        String encrypted = encryptor.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("같은 평문도 IV가 달라 매번 다른 암호문을 낸다")
    void randomIv() {
        assertThat(encryptor.encrypt("token")).isNotEqualTo(encryptor.encrypt("token"));
    }

    @Test
    @DisplayName("isEncrypted는 우리 형식 암호문만 true")
    void isEncrypted() {
        assertThat(encryptor.isEncrypted(encryptor.encrypt("token"))).isTrue();
        assertThat(encryptor.isEncrypted("legacy-plaintext-token")).isFalse();
        assertThat(encryptor.isEncrypted(null)).isFalse();
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void wrongKey() {
        String encrypted = encryptor.encrypt("token");

        RefreshTokenEncryptor other = new RefreshTokenEncryptor("different-key");

        assertThat(other.isEncrypted(encrypted)).isFalse();
    }
}
