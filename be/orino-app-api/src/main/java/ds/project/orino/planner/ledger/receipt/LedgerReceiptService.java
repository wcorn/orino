package ds.project.orino.planner.ledger.receipt;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionReceipt;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionReceiptRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 영수증 첨부(`LDG-016`).
 *
 * <p>바이트는 브라우저가 MinIO에 직접 올리고, 여기서는 <b>키를 원장에 묶는 일</b>만 한다.
 *
 * <p>첨부를 떼어내도 <b>오브젝트는 남는다</b>. 첨부는 실수로 지울 수 있고, 그때 되돌릴 수 있어야
 * 한다 — 회수는 보존 배치의 몫이다({@code VisitRetentionScheduler} 선례).
 */
@Service
public class LedgerReceiptService {

    private final LedgerTransactionReceiptRepository receiptRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerReceiptStorageService storageService;

    public LedgerReceiptService(LedgerTransactionReceiptRepository receiptRepository,
                                LedgerTransactionRepository transactionRepository,
                                LedgerReceiptStorageService storageService) {
        this.receiptRepository = receiptRepository;
        this.transactionRepository = transactionRepository;
        this.storageService = storageService;
    }

    @Transactional
    public LedgerReceiptDtos.View attach(Long memberId, Long transactionId,
                                         LedgerReceiptDtos.AttachRequest request) {
        transactionRepository.findByIdAndMemberIdAndDeletedAtIsNull(transactionId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_TRANSACTION_NOT_FOUND));

        int order = receiptRepository
                .findAllByMemberIdAndTransactionIdOrderByDisplayOrderAscIdAsc(memberId, transactionId)
                .size();
        LedgerTransactionReceipt receipt = receiptRepository.save(new LedgerTransactionReceipt(
                memberId, transactionId, request.objectKey(),
                request.contentType(), request.byteSize(), order));
        return view(receipt);
    }

    @Transactional(readOnly = true)
    public List<LedgerReceiptDtos.View> list(Long memberId, Long transactionId) {
        List<LedgerReceiptDtos.View> views = new ArrayList<>();
        for (LedgerTransactionReceipt receipt : receiptRepository
                .findAllByMemberIdAndTransactionIdOrderByDisplayOrderAscIdAsc(memberId, transactionId)) {
            views.add(view(receipt));
        }
        return views;
    }

    /** 첨부를 뗀다. <b>오브젝트는 지우지 않는다</b> — 되돌릴 수 있어야 한다. */
    @Transactional
    public void detach(Long memberId, Long receiptId) {
        LedgerTransactionReceipt receipt = receiptRepository
                .findByIdAndMemberId(receiptId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_TRANSACTION_NOT_FOUND));
        receiptRepository.delete(receipt);
    }

    private LedgerReceiptDtos.View view(LedgerTransactionReceipt receipt) {
        return new LedgerReceiptDtos.View(
                receipt.getId(),
                receipt.getObjectKey(),
                storageService.toPublicUrl(receipt.getObjectKey()),
                receipt.getContentType(),
                receipt.getByteSize(),
                receipt.getDisplayOrder());
    }
}
