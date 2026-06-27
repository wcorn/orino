package ds.project.orino.domain.planner.dayplan.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 데일리 플랜 — "반복되는 하루 템플릿". orino가 진실(Google 무관).
 *
 * <p>반복 규칙은 RRULE 문자열이 아니라 컬럼으로 분해 저장한다({@code freq/interval_val/by_day/by_month_day/
 * starts_on/until}). {@code by_day}는 CSV(예: "MO,WE,FR"), {@code by_month_day}는 CSV(예: "1,15").
 * 블록은 declarative 전량 교체(요청이 최종 상태)로 관리하며 cascade·orphanRemoval로 동기화한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "day_plan")
public class DayPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayPlanFreq freq;

    @Column(name = "interval_val", nullable = false)
    private int intervalVal;

    /** WEEKLY 요일 CSV(예: "MO,WE,FR"). 비-WEEKLY면 null. */
    @Column(name = "by_day", length = 40)
    private String byDay;

    /** MONTHLY 일자 CSV(예: "1,15"). 비-MONTHLY면 null. */
    @Column(name = "by_month_day", length = 80)
    private String byMonthDay;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column
    private LocalDate until;

    @Column(nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DayPlanBlock> blocks = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected DayPlan() {
    }

    public DayPlan(Long memberId, String name, String color, DayPlanFreq freq, int intervalVal,
                   String byDay, String byMonthDay, LocalDate startsOn, LocalDate until) {
        this.memberId = memberId;
        this.name = name;
        this.color = color;
        this.freq = freq;
        this.intervalVal = intervalVal;
        this.byDay = byDay;
        this.byMonthDay = byMonthDay;
        this.startsOn = startsOn;
        this.until = until;
        this.enabled = true;
    }

    /** 플랜 메타(이름·색·반복 규칙)를 갱신한다. 블록은 별도 교체. */
    public void updateMeta(String name, String color, DayPlanFreq freq, int intervalVal,
                           String byDay, String byMonthDay, LocalDate startsOn, LocalDate until) {
        this.name = name;
        this.color = color;
        this.freq = freq;
        this.intervalVal = intervalVal;
        this.byDay = byDay;
        this.byMonthDay = byMonthDay;
        this.startsOn = startsOn;
        this.until = until;
    }

    public void addBlock(DayPlanBlock block) {
        blocks.add(block);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public DayPlanFreq getFreq() {
        return freq;
    }

    public int getIntervalVal() {
        return intervalVal;
    }

    public String getByDay() {
        return byDay;
    }

    public String getByMonthDay() {
        return byMonthDay;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getUntil() {
        return until;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<DayPlanBlock> getBlocks() {
        return blocks;
    }
}
