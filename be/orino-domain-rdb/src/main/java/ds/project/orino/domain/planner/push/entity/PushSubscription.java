package ds.project.orino.domain.planner.push.entity;

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 웹푸시 구독 하나 — 기기 하나가 "나에게 보내도 좋다"고 등록한 주소.
 *
 * <p>{@code endpoint}는 푸시 서비스가 주는 URL로 수백 자에 이른다. MySQL 유니크 인덱스는 키
 * 길이 상한이 있어 그대로 걸 수 없어서, SHA-256 해시에 유니크를 건다. 해시는 <b>비밀이 아니라
 * 길이를 고정하려는 것</b>이다.
 *
 * <p>{@code p256dh}·{@code auth}는 브라우저가 준 구독 키다. 이 값으로 페이로드를 종단
 * 암호화하므로 서버가 잃어버리면 그 구독으로는 다시 보낼 수 없다 — 재구독만이 답이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * DDL이 {@code TEXT}라 명시적으로 맞춘다. {@code @Lob}을 쓰면 Hibernate가 {@code tinytext}로
     * 보고 validate에서 깨진다 — {@code BOOLEAN}을 {@code BIT(1)}로 맞춘 것과 같은 종류다.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64)
    private String endpointHash;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushSubscription() {
    }

    public PushSubscription(Long memberId, String endpoint, String p256dh,
                            String auth, String userAgent) {
        this.memberId = memberId;
        this.endpoint = endpoint;
        this.endpointHash = hash(endpoint);
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = truncate(userAgent);
    }

    /**
     * 같은 기기가 다시 구독했을 때. 브라우저는 키를 새로 만들어 주기도 하므로
     * <b>덮어써야</b> 한다 — 옛 키로 암호화하면 기기가 못 푼다.
     */
    public void refresh(String p256dh, String auth, String userAgent) {
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = truncate(userAgent);
    }

    /** endpoint의 SHA-256(hex). 유니크 인덱스에 쓸 고정 길이를 만든다. */
    public static String hash(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    /** UA 문자열은 길이 제한이 없다 — 컬럼을 넘기면 저장 자체가 실패한다. */
    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getEndpointHash() {
        return endpointHash;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
