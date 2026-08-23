package ds.project.orino.domain.planner.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 목적지 교체 이력 한 줄. <b>최초 발급도 한 줄이다</b>(명세 §5.1) — 상세 화면 이력의
 * 마지막 줄이 그것이고, 그래서 이력이 비어 있는 링크는 없다.
 *
 * <p>이력은 고쳐 쓰지 않는다. 갈아끼울 때마다 새 줄이 쌓이고, 지우는 API도 없다.
 */
@Entity
@Table(name = "shortlink_target_history")
public class ShortlinkTargetHistory {

    /** 발급 시 함께 남기는 첫 줄의 사유. */
    public static final String INITIAL_REASON = "최초 발급";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortlink_id", nullable = false)
    private Long shortlinkId;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(length = 255)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ShortlinkTargetHistory() {
    }

    public ShortlinkTargetHistory(Long shortlinkId, String targetUrl, String reason, Instant changedAt) {
        this.shortlinkId = shortlinkId;
        this.targetUrl = targetUrl;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public static ShortlinkTargetHistory initial(Long shortlinkId, String targetUrl, Instant changedAt) {
        return new ShortlinkTargetHistory(shortlinkId, targetUrl, INITIAL_REASON, changedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getShortlinkId() {
        return shortlinkId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
