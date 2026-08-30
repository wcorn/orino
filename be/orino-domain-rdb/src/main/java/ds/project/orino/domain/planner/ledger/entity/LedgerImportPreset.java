package ds.project.orino.domain.planner.ledger.entity;

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
 * 컬럼 매핑 프리셋.
 *
 * <p>같은 카드사 명세서를 매달 다시 매핑하지 않기 위한 것이다. 열 이름은 소스마다 제각각이라
 * 스키마로 못 박을 수 없어 <b>JSON 한 덩이</b>로 둔다 — 여기에 컬럼을 세우면 새 소스를 만날
 * 때마다 마이그레이션을 해야 한다.
 *
 * <p>{@code memberId}가 {@code null}인 행은 <b>동봉 프리셋</b>이다. 누구의 것도 아니고
 * 지울 수도 없다 — 처음 쓰는 사람이 맨 화면에서 시작하지 않게 하는 것이 목적이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_import_preset")
public class LedgerImportPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false, length = 60)
    private String name;

    // TEXT는 @Lob이 아니라 이 애너테이션으로 잡는다 — @Lob은 MySQL에서 LONGTEXT를 기대해
    // Hibernate validate가 스키마와 어긋난다고 본다.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "mapping_json", nullable = false)
    private String mappingJson;

    /** 머리글이 몇 줄인가. 카드사 명세서는 위에 안내문이 붙어 오는 일이 흔하다. */
    @Column(name = "skip_rows", nullable = false)
    private int skipRows;

    @Column(name = "date_format", length = 40)
    private String dateFormat;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerImportPreset() {
    }

    public LedgerImportPreset(Long memberId, String name, String mappingJson,
                              int skipRows, String dateFormat) {
        this.memberId = memberId;
        this.name = name;
        this.mappingJson = mappingJson;
        this.skipRows = skipRows;
        this.dateFormat = dateFormat;
    }

    public void update(String name, String mappingJson, Integer skipRows, String dateFormat) {
        if (name != null) {
            this.name = name;
        }
        if (mappingJson != null) {
            this.mappingJson = mappingJson;
        }
        if (skipRows != null) {
            this.skipRows = skipRows;
        }
        if (dateFormat != null) {
            this.dateFormat = dateFormat;
        }
    }

    /** 동봉 프리셋인가. 고칠 수도 지울 수도 없다. */
    public boolean isBuiltIn() {
        return memberId == null;
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

    public String getMappingJson() {
        return mappingJson;
    }

    public int getSkipRows() {
        return skipRows;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
