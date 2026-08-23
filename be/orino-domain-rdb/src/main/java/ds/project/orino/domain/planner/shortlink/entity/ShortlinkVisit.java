package ds.project.orino.domain.planner.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 방문 원시 기록 하나. <b>90일 뒤 삭제된다</b>(명세 §8.3).
 *
 * <p><b>IP 필드가 없다. 없는 것이 설계다</b>(명세 §8.1). 국가는 판정 결과 2자만 남기고 IP는
 * 판정 직후 버린다. User-Agent 원문도, 전체 리퍼러 URL도 여기 들어오지 않는다 —
 * "나중에 필요할지 모르니 일단 저장"이 프라이버시 원칙이 무너지는 전형적인 경로다.
 *
 * <p>나중에 "분석에 필요하니 원문도 넣자"는 요구가 오면 이 문단을 근거로 거절한다.
 */
@Entity
@Table(name = "shortlink_visit")
public class ShortlinkVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortlink_id", nullable = false)
    private Long shortlinkId;

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;

    /** 도메인까지만. 저장 시점에 잘라서 넣는다. */
    @Column(name = "referrer_domain", length = 255)
    private String referrerDomain;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VisitDevice device;

    /** ISO 3166-1 alpha-2. 판정 수단은 #1241에서 붙는다. */
    @Column(length = 2)
    private String country;

    /**
     * 봇·프리뷰 여부. <b>봇에게도 리다이렉트는 정상으로 내준다</b> — 프리뷰 카드가 떠야 한다.
     * 세는 자리만 나눈다(명세 §8.2).
     */
    @Column(name = "is_bot", nullable = false)
    private boolean bot;

    protected ShortlinkVisit() {
    }

    public ShortlinkVisit(Long shortlinkId, Instant visitedAt, String referrerDomain,
                          VisitDevice device, String country, boolean bot) {
        this.shortlinkId = shortlinkId;
        this.visitedAt = visitedAt;
        this.referrerDomain = referrerDomain;
        this.device = device;
        this.country = country;
        this.bot = bot;
    }

    public Long getId() {
        return id;
    }

    public Long getShortlinkId() {
        return shortlinkId;
    }

    public Instant getVisitedAt() {
        return visitedAt;
    }

    public String getReferrerDomain() {
        return referrerDomain;
    }

    public VisitDevice getDevice() {
        return device;
    }

    public String getCountry() {
        return country;
    }

    public boolean isBot() {
        return bot;
    }
}
