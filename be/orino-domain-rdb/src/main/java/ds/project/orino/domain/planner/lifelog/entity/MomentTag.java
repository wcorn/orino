package ds.project.orino.domain.planner.lifelog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기록 태그 하나. 정규화하지 않고 이름을 그대로 담는다(단일 사용자·저볼륨). 자동완성은
 * {@code SELECT DISTINCT name}으로 충분하다. 한 기록에 같은 이름은 유니크 제약으로 한 번만.
 */
@Entity
@Table(name = "moment_tag")
public class MomentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moment_id", nullable = false)
    private Long momentId;

    @Column(nullable = false, length = 50)
    private String name;

    protected MomentTag() {
    }

    public MomentTag(Long momentId, String name) {
        this.momentId = momentId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getMomentId() {
        return momentId;
    }

    public String getName() {
        return name;
    }
}
