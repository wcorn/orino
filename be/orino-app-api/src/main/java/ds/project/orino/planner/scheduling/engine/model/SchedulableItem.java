package ds.project.orino.planner.scheduling.engine.model;

import ds.project.orino.domain.calendar.entity.BlockType;

import java.time.LocalDate;

/**
 * 스케줄링 엔진이 배치할 항목 하나.
 * 내부 분류(category), 참조 엔티티(blockType/referenceId),
 * 소요 시간, 정렬 보조 키를 담는다.
 */
public final class SchedulableItem {

    private final ItemCategory category;
    private final BlockType blockType;
    private final long referenceId;
    private final int estimatedMinutes;
    /** 데드라인/예정일 (없으면 null). 가까운 순으로 정렬. */
    private final LocalDate due;
    /** 카테고리 내 보조 정렬 키 (낮을수록 먼저). */
    private final int subOrder;
    /** 연관 학습 자료 id (시간 할당 조회용, 없으면 null). */
    private final Long materialId;
    /** 디스플레이/디버그용. */
    private final String title;

    private SchedulableItem(Builder b) {
        this.category = b.category;
        this.blockType = b.blockType;
        this.referenceId = b.referenceId;
        this.estimatedMinutes = b.estimatedMinutes;
        this.due = b.due;
        this.subOrder = b.subOrder;
        this.materialId = b.materialId;
        this.title = b.title;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ItemCategory category() {
        return category;
    }

    public BlockType blockType() {
        return blockType;
    }

    public long referenceId() {
        return referenceId;
    }

    public int estimatedMinutes() {
        return estimatedMinutes;
    }

    public LocalDate due() {
        return due;
    }

    public int subOrder() {
        return subOrder;
    }

    public Long materialId() {
        return materialId;
    }

    public String title() {
        return title;
    }

    public static final class Builder {
        private ItemCategory category;
        private BlockType blockType;
        private long referenceId;
        private int estimatedMinutes;
        private LocalDate due;
        private int subOrder;
        private Long materialId;
        private String title;

        public Builder category(ItemCategory category) {
            this.category = category;
            return this;
        }

        public Builder blockType(BlockType blockType) {
            this.blockType = blockType;
            return this;
        }

        public Builder referenceId(long referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder estimatedMinutes(int estimatedMinutes) {
            this.estimatedMinutes = estimatedMinutes;
            return this;
        }

        public Builder due(LocalDate due) {
            this.due = due;
            return this;
        }

        public Builder subOrder(int subOrder) {
            this.subOrder = subOrder;
            return this;
        }

        public Builder materialId(Long materialId) {
            this.materialId = materialId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public SchedulableItem build() {
            if (category == null || blockType == null
                    || estimatedMinutes <= 0) {
                throw new IllegalStateException(
                        "필수 필드 누락: category/blockType/estimatedMinutes");
            }
            return new SchedulableItem(this);
        }
    }
}
