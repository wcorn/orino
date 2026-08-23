package ds.project.orino.domain.planner.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 짧은 주소 1건. 이 모듈의 SSOT다.
 *
 * <p><b>슬러그는 불변이다</b>(명세 §5.2). 바꿀 수 있으면 이미 뿌린 주소가 죽고, 비어 버린
 * 옛 슬러그가 재발급 가능해져 영구 점유(§3.1)도 함께 무너진다. 그래서 이 클래스에
 * {@code changeSlug}에 해당하는 메서드가 <b>없다</b>.
 *
 * <p>목적지는 바뀐다 — 그게 이 모듈의 존재 이유다. 교체할 때마다
 * {@link ShortlinkTargetHistory} 한 줄이 남고, 최초 발급도 그 이력의 첫 줄이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "shortlink")
public class Shortlink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 소문자로 정규화해 저장한다. 불변. */
    @Column(nullable = false, length = 32)
    private String slug;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(length = 255)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShortlinkStatus status;

    @Column(nullable = false)
    private boolean favorite;

    /**
     * 사용자가 슬러그를 직접 지었는지. 길이로는 판정할 수 없어(5자 커스텀 슬러그가 자동
     * 발급과 같아진다) 발급 시점에 확정해 담는다.
     */
    @Column(name = "custom_slug", nullable = false)
    private boolean customSlug;

    /** BCrypt 해시. NULL이면 비밀번호 없음. 확인 화면은 #1244. */
    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    /** NULL이면 만료 없음. 만료 여부는 저장하지 않고 조회 시 이 값과 현재 시각으로 판정한다. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "og_title", length = 255)
    private String ogTitle;

    @Column(name = "og_image_url", length = 1024)
    private String ogImageUrl;

    @Column(name = "og_fetched_at")
    private Instant ogFetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Shortlink() {
    }

    public Shortlink(Long memberId, String slug, String targetUrl, boolean customSlug) {
        this.memberId = memberId;
        this.slug = slug;
        this.targetUrl = targetUrl;
        this.customSlug = customSlug;
        this.status = ShortlinkStatus.ACTIVE;
        this.favorite = false;
    }

    /**
     * 목적지를 갈아끼운다. 값이 실제로 달라졌을 때만 {@code true}를 돌려준다 —
     * 호출자는 이 값이 참일 때만 이력을 남긴다(API 설계 §2 PATCH).
     */
    public boolean changeTarget(String newTargetUrl) {
        if (this.targetUrl.equals(newTargetUrl)) {
            return false;
        }
        this.targetUrl = newTargetUrl;
        return true;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public void updateExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateOgPreview(String ogTitle, String ogImageUrl, Instant ogFetchedAt) {
        this.ogTitle = ogTitle;
        this.ogImageUrl = ogImageUrl;
        this.ogFetchedAt = ogFetchedAt;
    }

    /** 활성 ↔ 비활성. 삭제된 링크는 되돌릴 수 없으므로 여기서 다루지 않는다. */
    public void toggleStatus() {
        this.status = this.status == ShortlinkStatus.ACTIVE
                ? ShortlinkStatus.DISABLED
                : ShortlinkStatus.ACTIVE;
    }

    public boolean toggleFavorite() {
        this.favorite = !this.favorite;
        return this.favorite;
    }

    /**
     * 소프트 삭제. <b>행을 지우지 않는다</b> — 이 행이 남아 있어야 {@code UNIQUE(slug)}가
     * 슬러그 재사용을 막는다(명세 §3.1). 예전에 그 주소를 받은 사람의 대화창에는 주소가
     * 영원히 남아 있고, 재활용은 그 사람을 예고 없이 다른 곳으로 보낸다.
     */
    public void softDelete(Instant deletedAt) {
        this.status = ShortlinkStatus.DELETED;
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /** 만료 여부는 저장값이 아니라 이 계산이다(데이터 모델 §3). */
    public boolean isExpiredAt(Instant now) {
        return this.expiresAt != null && !this.expiresAt.isAfter(now);
    }

    public boolean hasPassword() {
        return this.passwordHash != null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getMemo() {
        return memo;
    }

    public ShortlinkStatus getStatus() {
        return status;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public boolean isCustomSlug() {
        return customSlug;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getOgTitle() {
        return ogTitle;
    }

    public String getOgImageUrl() {
        return ogImageUrl;
    }

    public Instant getOgFetchedAt() {
        return ogFetchedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
