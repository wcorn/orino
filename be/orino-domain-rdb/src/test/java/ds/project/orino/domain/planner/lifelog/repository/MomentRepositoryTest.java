package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.lifelog.entity.Moment;
import ds.project.orino.domain.planner.lifelog.entity.MomentPhoto;
import ds.project.orino.domain.planner.lifelog.entity.MomentTag;
import ds.project.orino.domain.planner.lifelog.entity.Mood;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moment 및 사진·태그 매핑과 조회 규칙을 고정한다. FK cascade는 이 모듈이 {@code create-drop}으로
 * 엔티티에서 스키마를 만들어(FK 없음) 검증할 수 없다 — Liquibase 스키마의 몫이라 app-api 통합
 * 테스트·로컬 확인에서 본다.
 */
@RepositoryTest
@Transactional
class MomentRepositoryTest {

    @Autowired
    private MomentRepository momentRepository;
    @Autowired
    private MomentPhotoRepository photoRepository;
    @Autowired
    private MomentTagRepository tagRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("loguser", "pw")).getId();
    }

    @Test
    @DisplayName("리치 필드(기분·위치·발생시각)가 그대로 저장·조회된다")
    void savesAndLoadsRichFields() {
        Moment saved = momentRepository.save(new Moment(memberId,
                Instant.parse("2026-07-20T14:30:00Z"), "성산일출봉 정상", Mood.EXCITED,
                new BigDecimal("33.4580000"), new BigDecimal("126.9420000"), "성산일출봉"));

        Moment found = momentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getMemberId()).isEqualTo(memberId);
        assertThat(found.getOccurredAt()).isEqualTo(Instant.parse("2026-07-20T14:30:00Z"));
        assertThat(found.getBody()).isEqualTo("성산일출봉 정상");
        assertThat(found.getMood()).isEqualTo(Mood.EXCITED);
        assertThat(found.getLat()).isEqualByComparingTo("33.4580000");
        assertThat(found.getLng()).isEqualByComparingTo("126.9420000");
        assertThat(found.getPlaceName()).isEqualTo("성산일출봉");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사진 없는 텍스트 기록도 저장된다(위치·기분 null 허용)")
    void savesTextOnlyMoment() {
        Moment saved = momentRepository.save(new Moment(memberId,
                Instant.parse("2026-07-19T09:12:00Z"), "오늘 커피 맛있었다", null, null, null, null));

        Moment found = momentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getMood()).isNull();
        assertThat(found.getLat()).isNull();
        assertThat(found.getPlaceName()).isNull();
    }

    @Test
    @DisplayName("findByIdAndMemberId는 다른 멤버의 기록을 넘겨주지 않는다")
    void scopedByMember() {
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        Long id = momentRepository.save(new Moment(memberId,
                Instant.parse("2026-07-20T00:00:00Z"), "mine", null, null, null, null)).getId();

        assertThat(momentRepository.findByIdAndMemberId(id, memberId)).isPresent();
        assertThat(momentRepository.findByIdAndMemberId(id, other)).isEmpty();
    }

    @Test
    @DisplayName("피드는 발생시각 역순, 동시각이면 id 역순")
    void feedOrdersByOccurredAtDescThenIdDesc() {
        Long a = momentRepository.save(moment("2026-07-20T10:00:00Z")).getId();
        Long b = momentRepository.save(moment("2026-07-21T10:00:00Z")).getId();
        // 같은 발생시각 두 건 — 나중에 저장된 id가 앞서야.
        Long c1 = momentRepository.save(moment("2026-07-19T10:00:00Z")).getId();
        Long c2 = momentRepository.save(moment("2026-07-19T10:00:00Z")).getId();

        List<Long> order = momentRepository
                .findAllByMemberIdOrderByOccurredAtDescIdDesc(memberId)
                .stream().map(Moment::getId).toList();

        assertThat(order).containsExactly(b, a, c2, c1);
    }

    @Test
    @DisplayName("사진은 sort_order 오름차순으로, 여러 기록을 배치로 읽는다")
    void photosOrderedAndBatched() {
        Long m1 = momentRepository.save(moment("2026-07-20T10:00:00Z")).getId();
        Long m2 = momentRepository.save(moment("2026-07-20T11:00:00Z")).getId();
        photoRepository.save(new MomentPhoto(m1, "k/b.jpg", "k/b_t.jpg", 100, 100,
                Instant.parse("2026-07-20T10:00:00Z"), null, null, 1));
        photoRepository.save(new MomentPhoto(m1, "k/a.jpg", "k/a_t.jpg", 100, 100, null, null, null, 0));
        photoRepository.save(new MomentPhoto(m2, "k/c.jpg", null, null, null, null, null, null, 0));

        assertThat(photoRepository.findAllByMomentIdOrderBySortOrderAscIdAsc(m1))
                .extracting(MomentPhoto::getObjectKey)
                .containsExactly("k/a.jpg", "k/b.jpg");
        assertThat(photoRepository.findAllByMomentIdInOrderBySortOrderAscIdAsc(List.of(m1, m2)))
                .hasSize(3);
    }

    @Test
    @DisplayName("태그 자동완성은 멤버가 쓴 태그 중 접두어 일치를 중복 없이 정렬해 준다")
    void tagAutocompleteDistinctPrefixScoped() {
        Long m1 = momentRepository.save(moment("2026-07-20T10:00:00Z")).getId();
        Long m2 = momentRepository.save(moment("2026-07-20T11:00:00Z")).getId();
        tagRepository.save(new MomentTag(m1, "제주"));
        tagRepository.save(new MomentTag(m2, "제주"));      // 중복 이름
        tagRepository.save(new MomentTag(m1, "제주도"));
        tagRepository.save(new MomentTag(m1, "여행"));
        // 다른 멤버의 태그는 섞이면 안 된다.
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        Long om = momentRepository.save(new Moment(other,
                Instant.parse("2026-07-20T10:00:00Z"), null, null, null, null, null)).getId();
        tagRepository.save(new MomentTag(om, "제주비밀"));

        assertThat(tagRepository.findDistinctNamesByMemberIdAndPrefix(memberId, "제주%"))
                .containsExactly("제주", "제주도");
    }

    private Moment moment(String occurredAt) {
        return new Moment(memberId, Instant.parse(occurredAt), null, null, null, null, null);
    }
}
