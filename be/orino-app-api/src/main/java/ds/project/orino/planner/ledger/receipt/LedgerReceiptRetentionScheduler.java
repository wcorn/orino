package ds.project.orino.planner.ledger.receipt;

import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionReceiptRepository;
import ds.project.orino.planner.image.config.ImageStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 고아 영수증 오브젝트 회수(#1275).
 *
 * <p>영수증은 <b>지우는 길을 일부러 만들지 않았다</b>(#1270) — 거래를 소프트 삭제해도, 첨부를
 * 떼어내도 오브젝트는 남는다. 그래서 아무도 회수하지 않으면 MinIO에 영원히 쌓인다.
 * 이 배치가 그 몫을 맡는다.
 *
 * <p><b>고아의 정의는 하나다</b>: {@code ledger/receipts/**} 아래에 있는데 어느
 * {@code ledger_transaction_receipt} 행도 가리키지 않는 키. 소프트 삭제된 거래의 영수증은
 * <b>행이 남아 있으므로 고아가 아니다</b> — 되돌린 거래에서 영수증만 사라지는 일은 구조적으로
 * 일어나지 않는다.
 *
 * <p><b>유예 기간을 둔다.</b> 올린 직후의 오브젝트는 아직 아무 행도 가리키지 않는 것이 정상이다 —
 * 브라우저가 MinIO에 PUT을 끝냈지만 첨부 요청이 아직 안 왔을 수 있고, 그 창을 유예 없이 지우면
 * 방금 올린 영수증이 사라진다.
 *
 * <p><b>중복 실행에 안전하다.</b> 하는 일이 「가리키는 이 없는 키를 지운다」뿐이라 두 번 돌아도
 * 같은 키를 두 번 지울 뿐이다 — 잠금을 걸지 않는다(D-2와 같은 태도로, <b>안전함을 확인하고</b>
 * 생략한 것이지 replica 1에 기댄 것이 아니다).
 *
 * <p>버킷은 자체 호스팅(MinIO)이라 <b>과금이 아니라 디스크</b>다. 그래도
 * <a href="https://github.com/wcorn/orino/wiki/Periodic-Loops">주기 루프 인벤토리</a>에는
 * 올린다 — 외부 저장소를 주기적으로 훑는 루프는 세어 두는 편이 맞다.
 */
@Component
public class LedgerReceiptRetentionScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(LedgerReceiptRetentionScheduler.class);

    /** 영수증 오브젝트가 사는 곳. 같은 버킷의 다른 prefix는 건드리지 않는다. */
    static final String PREFIX = "ledger/receipts/";

    /**
     * 유예 기간. 올린 지 이만큼 지났는데도 아무도 안 가리키면 회수한다.
     *
     * <p>30일은 넉넉하다 — 업로드와 첨부 사이는 몇 초이고, 실수로 뗀 것을 알아채는 데
     * 한 달이면 충분하다.
     */
    static final Duration GRACE = Duration.ofDays(30);

    /** 한 번에 되물을 키 수. S3 목록 한 페이지와 맞춰 둔다. */
    private static final int BATCH = 1000;

    private final S3Client s3Client;
    private final ImageStorageProperties props;
    private final LedgerTransactionReceiptRepository receiptRepository;
    private final Clock clock;

    public LedgerReceiptRetentionScheduler(S3Client imageS3Client,
                                           ImageStorageProperties props,
                                           LedgerTransactionReceiptRepository receiptRepository,
                                           Clock clock) {
        this.s3Client = imageS3Client;
        this.props = props;
        this.receiptRepository = receiptRepository;
        this.clock = clock;
    }

    /** 새벽 3시 50분(KST). 방문 정리(3:30)와 카드 사이클 전환(4:10) 사이다. */
    @Scheduled(cron = "0 50 3 * * *", zone = "Asia/Seoul")
    public void purgeOrphanReceipts() {
        try {
            int deleted = purgeOn(clock.instant());
            if (deleted > 0) {
                log.info("가계부 영수증 고아 오브젝트 회수: {}건", deleted);
            }
        } catch (RuntimeException e) {
            // 못 지워도 서비스는 멀쩡하다. 디스크가 조금 더 쓰일 뿐이고 내일 다시 시도한다.
            log.warn("영수증 회수 실패: {}", e.getMessage());
        }
    }

    /** 「지금」을 밖에서 주는 경로. 테스트가 이 문으로 들어온다. */
    public int purgeOrphanReceiptsNow() {
        return purgeOn(clock.instant());
    }

    private int purgeOn(Instant now) {
        Instant threshold = now.minus(GRACE);
        int deleted = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                    .bucket(props.bucket())
                    .prefix(PREFIX)
                    .maxKeys(BATCH);
            if (continuationToken != null) {
                request.continuationToken(continuationToken);
            }
            ListObjectsV2Response page = s3Client.listObjectsV2(request.build());

            deleted += purgePage(page.contents(), threshold);
            continuationToken = Boolean.TRUE.equals(page.isTruncated())
                    ? page.nextContinuationToken() : null;
        } while (continuationToken != null);

        return deleted;
    }

    private int purgePage(List<S3Object> objects, Instant threshold) {
        // 유예 기간 안의 오브젝트는 아예 후보로 삼지 않는다 — 방금 올려 첨부를 기다리는 중일 수 있다.
        List<String> candidates = objects.stream()
                .filter(object -> object.lastModified().isBefore(threshold))
                .map(S3Object::key)
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }

        Set<String> referenced =
                new HashSet<>(receiptRepository.findReferencedKeys(candidates));
        List<ObjectIdentifier> orphans = new ArrayList<>();
        for (String key : candidates) {
            if (!referenced.contains(key)) {
                orphans.add(ObjectIdentifier.builder().key(key).build());
            }
        }
        if (orphans.isEmpty()) {
            return 0;
        }

        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(props.bucket())
                .delete(Delete.builder().objects(orphans).build())
                .build());
        return orphans.size();
    }
}
