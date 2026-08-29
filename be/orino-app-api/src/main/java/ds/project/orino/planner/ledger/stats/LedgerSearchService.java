package ds.project.orino.planner.ledger.stats;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.transaction.LedgerTransactionService;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 복합 검색(확정 명세 §10.2).
 *
 * <p>기간·금액 범위·자산·카테고리·태그·내용을 한 번에 건다. <b>결과를 그대로 일괄 편집에
 * 넘길 수 있어야</b> 쓸모가 생긴다 — 「작년 스타벅스 전부를 카페 카테고리로」 같은 정리가
 * 이 화면의 존재 이유이고, 그 일괄 편집은 이미 있는 {@code POST /transactions/bulk}가 맡는다.
 *
 * <p>여기서 새 일괄 편집 엔드포인트를 만들지 않는다 — 같은 일을 하는 문이 둘이 되면
 * 한쪽만 고쳐지는 날이 온다.
 */
@Service
public class LedgerSearchService {

    /** 한 번에 돌려줄 최대 건수. 일괄 편집에 넘길 목록이라 화면 한 장을 넘겨도 된다. */
    private static final int LIMIT = 500;

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerTransactionService transactionService;

    public LedgerSearchService(LedgerTransactionRepository transactionRepository,
                               LedgerTransactionService transactionService) {
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
    }

    /**
     * 조건을 <b>전부 AND</b>로 건다. 비운 조건은 걸지 않는다 — 「아무 조건 없이 부르면
     * 그 기간 전부」가 자연스럽고, 빈 값을 「일치하는 것 없음」으로 읽으면 화면이 이유 없이 빈다.
     */
    @Transactional(readOnly = true)
    public LedgerSearchDtos.SearchResponse search(Long memberId,
                                                  LedgerSearchDtos.SearchRequest request) {
        LocalDate from = request.from();
        LocalDate to = request.to();
        List<LedgerTransaction> rows = transactionRepository
                .findAllByMemberIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
                        memberId, from, to);

        String keyword = request.keyword() == null ? null
                : request.keyword().trim().toLowerCase();
        List<LedgerTransaction> matched = new ArrayList<>();
        long total = 0;
        for (LedgerTransaction row : rows) {
            if (!matches(row, request, keyword)) {
                continue;
            }
            if (matched.size() < LIMIT) {
                matched.add(row);
            }
            total += row.getType() == LedgerFlow.EXPENSE ? row.getAmount() : 0;
        }

        List<TransactionView> items = new ArrayList<>();
        for (LedgerTransaction row : matched) {
            items.add(transactionService.get(memberId, row.getId()));
        }
        return new LedgerSearchDtos.SearchResponse(items, matched.size(), total,
                matched.size() < countOf(rows, request, keyword));
    }

    private int countOf(List<LedgerTransaction> rows, LedgerSearchDtos.SearchRequest request,
                        String keyword) {
        int count = 0;
        for (LedgerTransaction row : rows) {
            if (matches(row, request, keyword)) {
                count++;
            }
        }
        return count;
    }

    private boolean matches(LedgerTransaction row, LedgerSearchDtos.SearchRequest request,
                            String keyword) {
        if (request.type() != null && row.getType() != request.type()) {
            return false;
        }
        if (request.assetId() != null && !request.assetId().equals(row.getAssetId())) {
            return false;
        }
        if (request.categoryId() != null && !request.categoryId().equals(row.getCategoryId())) {
            return false;
        }
        if (request.minAmount() != null && row.getAmount() < request.minAmount()) {
            return false;
        }
        if (request.maxAmount() != null && row.getAmount() > request.maxAmount()) {
            return false;
        }
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        // 내용과 메모를 함께 본다 — 「무엇을 샀나」가 둘 중 어디에 적혔는지는 사람마다 다르다.
        String title = row.getTitle() == null ? "" : row.getTitle().toLowerCase();
        String memo = row.getMemo() == null ? "" : row.getMemo().toLowerCase();
        return title.contains(keyword) || memo.contains(keyword);
    }
}
