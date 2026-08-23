package ds.project.orino.domain.planner.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 일별 집계. <b>영구 보존</b>이라 원시(90일)가 지워져도 막대 그래프는 남는다(명세 §8.3).
 *
 * <p>쓰기는 방문 시점의 UPSERT 하나뿐이고, 그 구문은
 * {@code ShortlinkVisitDailyRepository.accumulate}에 있다 — 이 엔티티로 값을 더하지 않는다.
 * 읽어서 +1 하고 저장하면 동시 방문 두 건이 하나로 뭉개진다.
 *
 * <p>{@code visitDate}는 <b>KST 날짜</b>다. 화면의 "오늘"과 어긋나면 안 된다.
 */
@Entity
@Table(name = "shortlink_visit_daily")
public class ShortlinkVisitDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortlink_id", nullable = false)
    private Long shortlinkId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    /** 사람 방문만. */
    @Column(name = "visit_count", nullable = false)
    private int visitCount;

    /** 봇·프리뷰. 따로 센다(명세 §8.2). */
    @Column(name = "bot_count", nullable = false)
    private int botCount;

    protected ShortlinkVisitDaily() {
    }

    public Long getId() {
        return id;
    }

    public Long getShortlinkId() {
        return shortlinkId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public int getBotCount() {
        return botCount;
    }
}
