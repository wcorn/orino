package ds.project.orino.domain.planner.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 링크 태그 하나. {@code moment_tag}와 같은 방식으로 정규화하지 않고 이름을 그대로 담는다
 * (단일 사용자·저볼륨). 사이드바의 태그별 개수는 {@code name} 인덱스로 집계한다.
 */
@Entity
@Table(name = "shortlink_tag")
public class ShortlinkTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortlink_id", nullable = false)
    private Long shortlinkId;

    @Column(nullable = false, length = 50)
    private String name;

    protected ShortlinkTag() {
    }

    public ShortlinkTag(Long shortlinkId, String name) {
        this.shortlinkId = shortlinkId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getShortlinkId() {
        return shortlinkId;
    }

    public String getName() {
        return name;
    }
}
