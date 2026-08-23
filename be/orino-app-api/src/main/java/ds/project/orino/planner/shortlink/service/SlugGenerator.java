package ds.project.orino.planner.shortlink.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 자동 슬러그 발급(명세 §4.2).
 *
 * <p><b>암호학적 난수를 쓴다.</b> 순차 채번이나 카운터 인코딩을 쓰면 다음에 발급될 주소를
 * 추측할 수 있게 되고, 그러면 "응답이 아무것도 알려주지 않는다"는 §7의 전제가 무너진다 —
 * 주소를 세어 볼 수 있는 순간 열거가 현실이 된다.
 *
 * <p>공간은 32⁵ ≈ 3,355만이다. 단일 사용자 규모에서 충돌은 재시도 몇 번으로 끝난다.
 */
@Component
public class SlugGenerator {

    /** 충돌 재시도 횟수. 여기까지 실패하면 사용자에게 다시 시도하라고 답한다(SL-ERR-005). */
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final ShortlinkRepository shortlinkRepository;

    public SlugGenerator(ShortlinkRepository shortlinkRepository) {
        this.shortlinkRepository = shortlinkRepository;
    }

    /**
     * 비어 있는 슬러그 하나를 뽑는다.
     *
     * <p>점유 판정에 <b>삭제된 링크도 포함된다</b>(명세 §3.1). 그래서 여유 공간은 "살아 있는
     * 링크 수"가 아니라 "지금까지 발급한 전부"만큼 줄어든다 — 3,355만에서는 문제가 아니다.
     *
     * @throws CustomException {@code SL-ERR-005} 5회 연속 충돌
     */
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String slug = randomSlug();
            if (!shortlinkRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new CustomException(ErrorCode.SHORTLINK_SLUG_EXHAUSTED);
    }

    private String randomSlug() {
        StringBuilder slug = new StringBuilder(SlugPolicy.AUTO_LENGTH);
        for (int i = 0; i < SlugPolicy.AUTO_LENGTH; i++) {
            slug.append(SlugPolicy.ALPHABET.charAt(random.nextInt(SlugPolicy.ALPHABET.length())));
        }
        return slug.toString();
    }
}
