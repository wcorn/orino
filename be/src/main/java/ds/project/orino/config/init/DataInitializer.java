package ds.project.orino.config.init;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataInitializer(MemberRepository memberRepository, BCryptPasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.count() > 0) {
            log.info("초기 계정이 이미 존재합니다. 스킵합니다.");
            return;
        }

        Member admin = new Member("admin", passwordEncoder.encode("admin"));
        memberRepository.save(admin);
        log.info("초기 계정 생성 완료: admin");
    }
}
