package ds.project.orino.planner.ledger.asset.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerAssetGroupKind;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 자산·그룹 쓰기 요청 묶음. 작은 record 넷을 파일 넷으로 흩지 않는다. */
public final class AssetRequests {

    private AssetRequests() {
    }

    /**
     * 자산 생성.
     *
     * @param linkedAssetId 체크카드면 <b>필수</b>다(LDG-ERR-019). 연결 계좌가 없으면 잔액이
     *                      어디서도 빠지지 않는 유령 자산이 된다(D-4)
     */
    public record Create(
            @NotBlank @Size(max = 60) String name,
            @NotNull LedgerAssetType type,
            Long groupId,
            @Size(max = 4) String accountLast4,
            Integer displayOrder,
            LocalDate maturityDate,
            Long targetAmount,
            Long linkedAssetId
    ) {
    }

    /**
     * 자산 수정. 보낸 것만 바꾼다.
     *
     * @param hidden 해지·닫음. <b>삭제가 아니다</b> — 자산을 지우면 과거 내역이 갈 곳을 잃는다
     */
    public record Update(
            @Size(max = 60) String name,
            Long groupId,
            Boolean clearGroup,
            @Size(max = 4) String accountLast4,
            Integer displayOrder,
            Boolean hidden,
            @Size(max = 30) String closedReason,
            LocalDate maturityDate,
            Long targetAmount,
            Long linkedAssetId
    ) {
    }

    public record GroupCreate(
            @NotBlank @Size(max = 60) String name,
            @NotNull LedgerAssetGroupKind kind,
            Integer displayOrder
    ) {
    }

    public record GroupUpdate(
            @Size(max = 60) String name,
            LedgerAssetGroupKind kind,
            Integer displayOrder,
            Boolean collapsed
    ) {
    }
}
