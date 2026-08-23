package ds.project.orino.domain.planner.shortlink.repository;

import ds.project.orino.domain.planner.shortlink.entity.Shortlink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShortlinkRepository extends JpaRepository<Shortlink, Long> {

    /**
     * 슬러그 점유 여부. <b>삭제된 링크도 포함한다</b> — 그게 영구 점유다(명세 §3.1).
     * 자동 발급의 충돌 재시도와 커스텀 슬러그 중복 검사가 같은 판정을 쓴다.
     */
    boolean existsBySlug(String slug);

    /** 관리 API 조회. 삭제된 링크는 없는 것과 같이 다룬다(목록에서 사라지고 상세는 404). */
    Optional<Shortlink> findBySlugAndMemberIdAndDeletedAtIsNull(String slug, Long memberId);

    /**
     * 공개 리다이렉트 조회. <b>멤버 스코프도 상태 조건도 걸지 않는다</b> — 방문자는 로그인하지
     * 않고, 꺼짐·만료·삭제의 판정은 조회한 뒤 한곳에서 한다. 조건을 쿼리에 흩어 놓으면
     * "넷이 같은 404"라는 계약(명세 §7)이 쿼리와 컨트롤러 양쪽에 걸쳐 버린다.
     */
    Optional<Shortlink> findBySlug(String slug);

    long countByMemberIdAndDeletedAtIsNull(Long memberId);

    /**
     * 목록 조회(검색·태그 필터). <b>상태 필터는 여기서 걸지 않는다</b> — 화면의 상태 칩 숫자가
     * "지금 검색어 안에서 활성/비활성이 몇 개인지"라, 상태로 걸러 버리면 나머지 칸의 숫자를
     * 셀 수 없다. 걸러진 목록을 받아 서비스가 상태로 나눈다.
     *
     * @param query 소문자화한 {@code %...%} 패턴. null이면 검색 없음
     * @param tag   태그명 정확 일치. null이면 태그 필터 없음
     */
    @Query("""
            SELECT s FROM Shortlink s
            WHERE s.memberId = :memberId
              AND s.deletedAt IS NULL
              AND (:query IS NULL
                   OR LOWER(s.slug) LIKE :query
                   OR LOWER(s.targetUrl) LIKE :query
                   OR LOWER(s.memo) LIKE :query)
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM ShortlinkTag t
                    WHERE t.shortlinkId = s.id AND t.name = :tag))
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<Shortlink> search(@Param("memberId") Long memberId,
                           @Param("query") String query,
                           @Param("tag") String tag);
}
