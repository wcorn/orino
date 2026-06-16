package ds.project.orino.domain.planner.google.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * {@code refresh_token} 컬럼 암복호화 JPA 컨버터. Spring(SpringBeanContainer)이 주입한다.
 *
 * <p>읽기 시 암호화된 값이면 복호화하고, 아니면(레거시 평문) 그대로 반환해 전환을 안전하게 한다.
 * 평문 row는 시작 시 마이그레이션 러너가 암호화한다.
 */
@Component
@Converter
public class RefreshTokenConverter implements AttributeConverter<String, String> {

    private final RefreshTokenEncryptor encryptor;

    public RefreshTokenConverter(RefreshTokenEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptor.isEncrypted(dbData) ? encryptor.decrypt(dbData) : dbData;
    }
}
