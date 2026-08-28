package ds.project.orino.planner.ledger.template;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionTemplate;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionTemplateRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerNames;
import ds.project.orino.planner.ledger.transaction.LedgerTransactionService;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreateRequest;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreatedResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 빠른 입력 템플릿(`LDG-013`).
 *
 * <p>같은 커피를 매일 다시 적는 일이 반복되면 사람은 가계부를 안 쓰게 된다. 성공 지표가
 * 「주 5일 이상 기록 · 1건당 30초」인데, 그건 첫 입력이 아니라 <b>200번째 입력</b>에서 갈린다.
 */
@Service
public class LedgerTemplateService {

    private final LedgerTransactionTemplateRepository templateRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerTransactionService transactionService;
    private final LedgerClock clock;

    public LedgerTemplateService(LedgerTransactionTemplateRepository templateRepository,
                                 LedgerAssetRepository assetRepository,
                                 LedgerCategoryRepository categoryRepository,
                                 LedgerTransactionService transactionService,
                                 LedgerClock clock) {
        this.templateRepository = templateRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    /** 많이 쓴 순. 대시보드 「빠른 입력」 칩이 이 순서를 그대로 쓴다. */
    @Transactional(readOnly = true)
    public List<LedgerTemplateDtos.View> list(Long memberId) {
        LedgerNames names = names(memberId);
        List<LedgerTemplateDtos.View> views = new ArrayList<>();
        for (LedgerTransactionTemplate template
                : templateRepository.findAllByMemberIdOrderByUseCountDescIdDesc(memberId)) {
            views.add(LedgerTemplateDtos.View.of(template,
                    names.assetName(template.getAssetId()),
                    names.categoryName(template.getCategoryId())));
        }
        return views;
    }

    @Transactional
    public LedgerTemplateDtos.View create(Long memberId, LedgerTemplateDtos.Create request) {
        requireAsset(memberId, request.assetId());
        if (request.categoryId() != null) {
            requireCategory(memberId, request.categoryId());
        }
        LedgerTransactionTemplate template = new LedgerTransactionTemplate(
                memberId, request.name(), request.txType(), request.amount(), request.assetId());
        template.updateCategoryId(request.categoryId());
        template.updateTitle(request.title());
        templateRepository.save(template);

        LedgerNames names = names(memberId);
        return LedgerTemplateDtos.View.of(template,
                names.assetName(template.getAssetId()),
                names.categoryName(template.getCategoryId()));
    }

    @Transactional
    public LedgerTemplateDtos.View update(Long memberId, Long id, LedgerTemplateDtos.Update request) {
        LedgerTransactionTemplate template = requireTemplate(memberId, id);
        if (request.assetId() != null) {
            requireAsset(memberId, request.assetId());
        }
        if (request.categoryId() != null) {
            requireCategory(memberId, request.categoryId());
        }
        template.update(request.name(), request.txType(), request.amount(),
                request.assetId(), request.categoryId(), request.title());

        LedgerNames names = names(memberId);
        return LedgerTemplateDtos.View.of(template,
                names.assetName(template.getAssetId()),
                names.categoryName(template.getCategoryId()));
    }

    /** 템플릿은 지운다. 원장이 아니라 <b>편의 설정</b>이라 남길 이유가 없다. */
    @Transactional
    public void delete(Long memberId, Long id) {
        templateRepository.delete(requireTemplate(memberId, id));
    }

    /**
     * 템플릿으로 한 건 적는다 — <b>언제나 오늘 날짜</b>다.
     *
     * <p>기록과 함께 {@code useCount}가 오른다. 그래야 자주 쓰는 것이 저절로 위로 올라오고,
     * 사람이 순서를 관리하지 않아도 된다.
     */
    @Transactional
    public TransactionCreatedResponse apply(Long memberId, Long id) {
        LedgerTransactionTemplate template = requireTemplate(memberId, id);
        template.recordUse();

        return transactionService.create(memberId, new TransactionCreateRequest(
                template.getTxType(),
                template.getAmount(),
                clock.today(),
                null,
                template.getAssetId(),
                null,
                template.getCategoryId(),
                template.getTitle(),
                null,
                List.of(),
                false,
                null,
                // 템플릿은 반복해서 쓰는 것이라 할부를 담지 않는다 — 매번 새 할부가 열린다.
                null));
    }

    private LedgerNames names(Long memberId) {
        return new LedgerNames(
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId),
                categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId));
    }

    private LedgerTransactionTemplate requireTemplate(Long memberId, Long id) {
        return templateRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_TRANSACTION_NOT_FOUND));
    }

    private void requireAsset(Long memberId, Long assetId) {
        assetRepository.findByIdAndMemberId(assetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
    }

    private void requireCategory(Long memberId, Long categoryId) {
        categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));
    }
}
