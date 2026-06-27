package ds.project.orino.domain.planner.google.entity;

import ds.project.orino.domain.planner.google.crypto.RefreshTokenConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Google OAuth 연동 자격증명. 단일 사용자라 1 row지만 {@code member_id} UNIQUE로 멀티유저 확장 여지를 남긴다.
 *
 * <p>refresh token은 영속 보관(연결 해제까지). access token/oauth-state는 Redis(휘발).
 * 일정/할 일은 Google이 source of truth이므로 orino에 저장하지 않는다.
 */
@Entity
@Table(name = "google_account")
@EntityListeners(AuditingEntityListener.class)
public class GoogleAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "google_email", length = 255)
    private String googleEmail;

    @Convert(converter = RefreshTokenConverter.class)
    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(length = 512)
    private String scopes;

    @Column(name = "primary_calendar_id", length = 255)
    private String primaryCalendarId;

    @Column(name = "task_list_id", length = 255)
    private String taskListId;

    /** 복습 미러용 보조 캘린더("orino 복습") ID. 최초 enable 시 생성·기록 후 재사용. */
    @Column(name = "review_calendar_id", length = 255)
    private String reviewCalendarId;

    /** 복습 → 보조 캘린더 단방향 미러 on/off 토글. */
    @Column(name = "review_mirror_enabled", nullable = false)
    private boolean reviewMirrorEnabled = false;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected GoogleAccount() {
    }

    public GoogleAccount(Long memberId, String refreshToken, String scopes,
                         String googleEmail, String primaryCalendarId, String taskListId) {
        this.memberId = memberId;
        this.refreshToken = refreshToken;
        this.scopes = scopes;
        this.googleEmail = googleEmail;
        this.primaryCalendarId = primaryCalendarId;
        this.taskListId = taskListId;
        this.connectedAt = Instant.now();
        this.revoked = false;
    }

    /** 재연동(re-consent): refresh token·메타데이터를 갱신하고 revoked를 해제한다. */
    public void reconnect(String refreshToken, String scopes, String googleEmail,
                          String primaryCalendarId, String taskListId) {
        this.refreshToken = refreshToken;
        this.scopes = scopes;
        this.googleEmail = googleEmail;
        this.primaryCalendarId = primaryCalendarId;
        this.taskListId = taskListId;
        this.connectedAt = Instant.now();
        this.revoked = false;
    }

    /** invalid_grant 등으로 refresh token이 무효화됐을 때 마킹. FE 재연동 CTA 유도. */
    public void markRevoked() {
        this.revoked = true;
    }

    /**
     * 복습 미러를 켠다. 보조 캘린더가 새로 생성됐으면 그 ID를 기록하고, 이미 있으면(재-enable) 기존 ID를 유지한다.
     */
    public void enableReviewMirror(String reviewCalendarId) {
        if (reviewCalendarId != null) {
            this.reviewCalendarId = reviewCalendarId;
        }
        this.reviewMirrorEnabled = true;
    }

    /** 복습 미러를 끈다. 빠른 재-enable을 위해 보조 캘린더 ID는 보존한다. */
    public void disableReviewMirror() {
        this.reviewMirrorEnabled = false;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getScopes() {
        return scopes;
    }

    public String getPrimaryCalendarId() {
        return primaryCalendarId;
    }

    public String getTaskListId() {
        return taskListId;
    }

    public String getReviewCalendarId() {
        return reviewCalendarId;
    }

    public boolean isReviewMirrorEnabled() {
        return reviewMirrorEnabled;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
