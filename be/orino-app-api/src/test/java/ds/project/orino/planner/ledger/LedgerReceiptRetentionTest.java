package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.ledger.receipt.LedgerReceiptRetentionScheduler;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import ds.project.orino.support.StubS3Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 영수증 고아 오브젝트 회수(#1275).
 *
 * <p>영수증은 지우는 길을 일부러 만들지 않았다(#1270) — 그래서 <b>이 배치가 유일한 회수
 * 경로</b>이고, 여기서 확인할 것은 「지우나」가 아니라 <b>지우면 안 되는 것을 안 지우나</b>다.
 *
 * <p>가장 중요한 것: <b>소프트 삭제된 거래의 영수증은 살아남는다.</b> 되돌린 거래에서 영수증만
 * 사라지면 그건 되돌린 게 아니다.
 */
@Import(StubExternalsConfig.class)
class LedgerReceiptRetentionTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private S3Client s3Client;
    @Autowired
    private LedgerReceiptRetentionScheduler scheduler;
    @Autowired
    private Clock clock;

    private String authHeader;
    private long checking;
    private StubS3Client bucket;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        bucket = (StubS3Client) s3Client;
        bucket.clear();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
    }

    /** 첨부돼 있는 오브젝트는 아무리 오래돼도 건드리지 않는다. */
    @Test
    @DisplayName("첨부된 영수증은 회수 대상이 아니다")
    void keepsAttached() throws Exception {
        String key = "ledger/receipts/1/attached.jpg";
        attach(newTransaction(), key);
        bucket.put(key, longAgo());

        assertThat(scheduler.purgeOrphanReceiptsNow()).isZero();
        assertThat(bucket.has(key)).isTrue();
    }

    /**
     * <b>이 테스트가 이 배치의 유일한 제약이다.</b> 소프트 삭제된 거래의 첨부 행은 남아 있으므로
     * 그 영수증은 고아가 아니다 — 되돌리면 영수증도 함께 돌아온다.
     */
    @Test
    @DisplayName("소프트 삭제된 거래의 영수증도 남는다 — 되돌리면 함께 돌아와야 한다")
    void keepsReceiptOfDeletedTransaction() throws Exception {
        long transactionId = newTransaction();
        String key = "ledger/receipts/1/deleted-tx.jpg";
        attach(transactionId, key);
        bucket.put(key, longAgo());

        mockMvc.perform(delete("/api/ledger/transactions/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        assertThat(scheduler.purgeOrphanReceiptsNow()).isZero();
        assertThat(bucket.has(key)).isTrue();
    }

    /** 첨부를 떼면 행이 사라진다 — 그때부터 그 오브젝트는 아무도 가리키지 않는다. */
    @Test
    @DisplayName("뗀 영수증의 오브젝트는 유예 기간이 지나면 회수된다")
    void collectsDetached() throws Exception {
        long transactionId = newTransaction();
        String key = "ledger/receipts/1/detached.jpg";
        long receiptId = attach(transactionId, key);
        bucket.put(key, longAgo());

        mockMvc.perform(delete("/api/ledger/receipts/" + receiptId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        assertThat(scheduler.purgeOrphanReceiptsNow()).isEqualTo(1);
        assertThat(bucket.has(key)).isFalse();
    }

    /**
     * 방금 올린 오브젝트는 아직 아무 행도 가리키지 않는 것이 <b>정상</b>이다 — 브라우저가
     * PUT을 끝냈지만 첨부 요청이 아직 안 왔을 수 있다. 유예 없이 지우면 그 창이 사고가 된다.
     */
    @Test
    @DisplayName("방금 올린 오브젝트는 아직 안 붙었어도 남는다")
    void keepsFreshUpload() {
        String key = "ledger/receipts/1/just-uploaded.jpg";
        bucket.put(key, clock.instant().minus(Duration.ofMinutes(5)));

        assertThat(scheduler.purgeOrphanReceiptsNow()).isZero();
        assertThat(bucket.has(key)).isTrue();
    }

    /** 같은 버킷에 사는 다른 prefix는 이 배치의 소관이 아니다. */
    @Test
    @DisplayName("영수증 prefix 밖은 훑지 않는다")
    void ignoresOtherPrefixes() {
        String key = "note-images/1/photo.png";
        bucket.put(key, longAgo());

        assertThat(scheduler.purgeOrphanReceiptsNow()).isZero();
        assertThat(bucket.has(key)).isTrue();
    }

    /** 하는 일이 「가리키는 이 없는 키를 지운다」뿐이라 두 번 돌아도 같은 결과다. */
    @Test
    @DisplayName("두 번 돌려도 안전하다 — 잠금을 걸지 않는 이유다")
    void idempotent() {
        bucket.put("ledger/receipts/1/orphan.jpg", longAgo());

        assertThat(scheduler.purgeOrphanReceiptsNow()).isEqualTo(1);
        assertThat(scheduler.purgeOrphanReceiptsNow()).isZero();
    }

    private Instant longAgo() {
        return clock.instant().minus(Duration.ofDays(60));
    }

    private long newTransaction() throws Exception {
        String body = LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 4500, "assetId": %d, "occurredOn": "2026-08-01"}
                """.formatted(checking));
        return LedgerFixture.transactionId(body);
    }

    private long attach(long transactionId, String objectKey) throws Exception {
        String body = mockMvc.perform(
                        post("/api/ledger/transactions/%d/receipts".formatted(transactionId))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"objectKey": "%s"}
                                        """.formatted(objectKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 첨부가 실제로 붙었는지 한 번은 눈으로 확인해 둔다 — 나머지 테스트의 전제다. */
    @Test
    @DisplayName("첨부하면 목록에 뜬다")
    void attachedShowsUp() throws Exception {
        long transactionId = newTransaction();
        attach(transactionId, "ledger/receipts/1/a.jpg");

        mockMvc.perform(
                        get("/api/ledger/transactions/%d/receipts".formatted(transactionId))
                                .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)));
    }
}
