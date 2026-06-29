package ds.project.orino.domain.planner.dayplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 주간 계획표의 시간 블록(한 칸). 멤버당 모든 블록의 집합이 곧 "단일 주간 템플릿"이다.
 * wrapper 엔티티 없이 {@code member_id}를 직접 보유하고 요일·구간만 가진다(반복·차임·미러 없음).
 *
 * <p>시간은 자정 기준 분(0~1440)으로 저장한다. {@code endMinute=1440}은 자정(24:00) 종료를 뜻한다
 * (LocalTime/TIME으로는 표현 불가해 분 정수로 둔다).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "day_plan_block")
public class DayPlanBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 요일 0=일 … 6=토. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    /** 시작(자정 기준 분, 0~1439). */
    @Column(name = "start_minute", nullable = false)
    private int startMinute;

    /** 종료(자정 기준 분, 1~1440). 항상 {@code > startMinute}. 1440=자정(24:00). */
    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 20)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected DayPlanBlock() {
    }

    public DayPlanBlock(Long memberId, int dayOfWeek, int startMinute, int endMinute,
                        String label, String color, int sortOrder) {
        this.memberId = memberId;
        this.dayOfWeek = dayOfWeek;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.label = label;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
