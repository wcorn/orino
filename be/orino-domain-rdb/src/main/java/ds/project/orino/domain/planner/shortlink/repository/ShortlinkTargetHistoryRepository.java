package ds.project.orino.domain.planner.shortlink.repository;

import ds.project.orino.domain.planner.shortlink.entity.ShortlinkTargetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShortlinkTargetHistoryRepository
        extends JpaRepository<ShortlinkTargetHistory, Long> {

    /** 상세 화면은 시간 역순으로 보여준다 — 맨 아래 줄이 최초 발급이다. */
    List<ShortlinkTargetHistory> findAllByShortlinkIdOrderByChangedAtDescIdDesc(Long shortlinkId);
}
