package ds.project.orino.support;

import ds.project.orino.domain.member.entity.Member;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class MemberFixture {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public static final String DEFAULT_LOGIN_ID = "admin";
    public static final String DEFAULT_PASSWORD = "password";

    public static Member create() {
        return create(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD);
    }

    public static Member create(String loginId, String rawPassword) {
        return new Member(loginId, ENCODER.encode(rawPassword));
    }
}
