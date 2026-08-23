package ds.project.orino.domain.planner.shortlink.repository;

import ds.project.orino.domain.planner.shortlink.entity.ShortlinkTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShortlinkTagRepository extends JpaRepository<ShortlinkTag, Long> {

    List<ShortlinkTag> findAllByShortlinkIdOrderByIdAsc(Long shortlinkId);

    /** 목록 배치 로딩: 여러 링크의 태그를 한 번에. */
    List<ShortlinkTag> findAllByShortlinkIdIn(Collection<Long> shortlinkIds);

    void deleteByShortlinkId(Long shortlinkId);

    /**
     * 사이드바 태그 목록 — 살아 있는 링크에 붙은 태그만, 개수 많은 순.
     * 삭제된 링크의 태그 행은 남아 있지만 화면에서는 없는 것과 같다.
     */
    @Query("""
            SELECT t.name AS name, COUNT(t.id) AS count
            FROM ShortlinkTag t
            WHERE t.shortlinkId IN (
                SELECT s.id FROM Shortlink s
                WHERE s.memberId = :memberId AND s.deletedAt IS NULL)
            GROUP BY t.name
            ORDER BY COUNT(t.id) DESC, t.name ASC
            """)
    List<TagCountProjection> countByMemberIdGroupByName(@Param("memberId") Long memberId);

    /** 태그명과 개수만 담는 조회 전용 투영. */
    interface TagCountProjection {
        String getName();

        long getCount();
    }
}
