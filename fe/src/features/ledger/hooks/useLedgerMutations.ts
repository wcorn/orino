import { useMutation, useQueryClient } from "@tanstack/react-query";

import { travelKeys } from "@/features/travel/queryKeys";
import { toast } from "@/shared/lib/toast";

import {
  applyTemplate,
  type AssetCreateRequest,
  type AssetUpdateRequest,
  attachReceipt,
  attachTripToTransactions,
  type BudgetPutRequest,
  bulkCreateTransactions,
  type CategoryAttributesRequest,
  confirmOccurrence,
  createAsset,
  createAutoRule,
  createPoint,
  createReceiptUploadUrl,
  createTemplate,
  createTransaction,
  deleteAsset,
  deleteAutoRule,
  deletePoint,
  deleteTemplate,
  deleteTransaction,
  detachReceipt,
  duplicateTransaction,
  endRecurring,
  executeImport,
  type ImportMapping,
  type LedgerMatchType,
  type OccurrenceConfirmRequest,
  type OccurrenceRequest,
  patchOccurrence,
  pauseRecurring,
  payStatement,
  putBudget,
  reconcileAsset,
  type ReconcileRequest,
  type RecurringEndRequest,
  resumeRecurring,
  revertImportBatch,
  type SettingsUpdateRequest,
  type StatementPayRequest,
  type TemplateCreateRequest,
  type TransactionCreatedResponse,
  type TransactionCreateRequest,
  type TransactionUpdateRequest,
  updateAsset,
  updateAutoRule,
  updateCategoryAttributes,
  updatePoint,
  updateSettings,
  updateTransaction,
  updateUsageGoal,
  type UsageGoalRequest,
} from "../api/ledger";
import { ledgerKeys } from "../queryKeys";

/**
 * 거래 하나가 잔액·요약·자산 상세를 전부 바꾼다. 셋을 따로 갱신하면 화면마다 다른 숫자가
 * 남고, 이 모듈에서 그건 「원장이 틀어졌다」와 구분되지 않는다.
 */
function invalidateAll(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ledgerKeys.all });
}

/**
 * 거래 입력.
 *
 * <p><b>낙관적으로 넣지 않는다.</b> 미래 날짜면 서버가 예정으로 바꿔 저장하고(`savedAs`),
 * 외화면 원화 환산액을 서버가 확정한다 — 화면이 미리 지어낸 줄은 둘 다 틀린다.
 * 링크 발급과 달리 여기서는 <b>서버가 정하는 값이 본문</b>이다.
 */
export function useCreateTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: TransactionCreateRequest) => createTransaction(body),
    onSuccess: (result: TransactionCreatedResponse) => {
      // 요청과 다르게 저장됐으면 반드시 알린다. 조용히 예정으로 넘기면 「입력이 안 됐다」로 읽힌다.
      if (result.savedAs === "SCHEDULED") {
        toast("미래 날짜라 예정으로 저장했어요", "info");
      } else {
        toast("저장했어요", "success");
      }
    },
    onError: () => toast("저장하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: number;
      body: TransactionUpdateRequest;
    }) => updateTransaction(id, body),
    onError: () => toast("수정하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/** 소프트 삭제. 서버에 행은 남는다 — 문구도 「지웠다」가 아니라 「삭제했다」로만 말한다. */
export function useDeleteTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteTransaction(id),
    onSuccess: () => toast("삭제했어요"),
    onError: () => toast("삭제하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useCreateAsset() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: AssetCreateRequest) => createAsset(body),
    onSuccess: () => toast("자산을 만들었어요"),
    onError: () => toast("자산을 만들지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useUpdateAsset() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: AssetUpdateRequest }) =>
      updateAsset(id, body),
    onError: () => toast("자산을 수정하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 자산 삭제.
 *
 * <p>실패 문구는 <b>사실만</b> 말한다. 「해지해 주세요」를 붙이면 이미 해지한 자산에게
 * 방금 한 일을 다시 하라는 말이 된다(#1316) — 다음 행동 안내는 상황을 아는 화면이 한다.
 * 애초에 지울 수 없는 자산은 버튼이 비활성이라, 이 길로 오는 것은 드문 경우다.
 */
export function useDeleteAsset() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteAsset(id),
    onSuccess: () => toast("자산을 삭제했어요"),
    onError: () => toast("거래가 있는 자산이라 삭제할 수 없어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 잔액 맞추기. 차이가 0이면 서버가 거래를 만들지 않고 그 사실을 알려 준다 —
 * 「이미 맞아요」와 「20,000원을 조정했어요」는 사용자에게 다른 소식이다.
 */
export function useReconcileAsset() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ReconcileRequest }) =>
      reconcileAsset(id, body),
    onSuccess: (result) => {
      toast(
        result.adjustmentTransactionId === null
          ? "이미 맞아요 — 조정할 것이 없었어요"
          : "잔액을 맞췄어요",
        result.adjustmentTransactionId === null ? "info" : "success",
      );
    },
    onError: () => toast("잔액을 맞추지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 템플릿으로 한 건 적기. 저장 결과 문구는 일반 입력과 같은 규칙을 쓴다 —
 * 어느 길로 적었든 「예정으로 갔다」는 반드시 알려야 한다.
 */
export function useApplyTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => applyTemplate(id),
    onSuccess: (result) =>
      toast(
        result.savedAs === "SCHEDULED"
          ? "미래 날짜라 예정으로 저장했어요"
          : "저장했어요",
        result.savedAs === "SCHEDULED" ? "info" : "success",
      ),
    onError: () => toast("저장하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useCreateTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: TemplateCreateRequest) => createTemplate(body),
    onSuccess: () => toast("템플릿으로 저장했어요"),
    onError: () => toast("템플릿을 만들지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useDeleteTemplate() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteTemplate(id),
    onError: () => toast("템플릿을 지우지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/** 내역 복사. 기본은 오늘 날짜다 — 대개 「같은 걸 오늘 또 썼다」이다. */
export function useDuplicateTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, useToday }: { id: number; useToday: boolean }) =>
      duplicateTransaction(id, useToday),
    onSuccess: () => toast("복사했어요"),
    onError: () => toast("복사하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 고른 거래를 여행에 붙인다(여행 v2.2 §18).
 *
 * <p>여행 요약도 함께 무효화한다 — 홈 카드와 경비 화면이 그 값을 읽으므로, 붙여 놓고
 * 여행으로 건너가면 방금 붙인 것이 안 보이는 상태가 된다.
 */
export function useAttachTripToTransactions() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ ids, tripId }: { ids: number[]; tripId: number | null }) =>
      attachTripToTransactions(ids, tripId),
    onSuccess: (result, { tripId }) =>
      toast(
        tripId === null
          ? `${result.affected}건의 여행 연결을 끊었어요`
          : `${result.affected}건을 여행에 붙였어요`,
        "success",
      ),
    onError: () => toast("붙이지 못했어요.", "error"),
    onSettled: () => {
      invalidateAll(queryClient);
      void queryClient.invalidateQueries({ queryKey: travelKeys.all });
    },
  });
}

/**
 * 다건 입력. 서버가 한 트랜잭션으로 처리하므로 <b>전부 들어갔거나 하나도 안 들어갔거나</b>다 —
 * 실패했을 때 「몇 건은 됐다」고 말하지 않는다.
 */
export function useBulkCreateTransactions() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (transactions: TransactionCreateRequest[]) =>
      bulkCreateTransactions(transactions),
    onSuccess: (result) => {
      const scheduled = result.scheduledCount;
      toast(
        scheduled > 0
          ? `${result.created.length}건을 저장했어요 — 그중 ${scheduled}건은 예정입니다`
          : `${result.created.length}건을 저장했어요`,
        "success",
      );
    },
    onError: () => toast("한 줄이라도 잘못되면 전부 저장하지 않아요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 영수증 첨부. 바이트는 브라우저가 MinIO에 직접 PUT 하고, 서버에는 키만 보낸다 —
 * 파일이 BE를 거치지 않는다.
 */
export function useAttachReceipt() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      transactionId,
      file,
    }: {
      transactionId: number;
      file: File;
    }) => {
      const target = await createReceiptUploadUrl(file.type);
      const uploaded = await fetch(target.uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": file.type },
        body: file,
      });
      if (!uploaded.ok) {
        throw new Error("upload failed");
      }
      return attachReceipt(transactionId, {
        objectKey: target.objectKey,
        contentType: file.type,
        byteSize: file.size,
      });
    },
    onError: () => toast("영수증을 올리지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useDetachReceipt() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => detachReceipt(id),
    onError: () => toast("영수증을 떼지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: SettingsUpdateRequest) => updateSettings(body),
    onSuccess: () => toast("설정을 저장했어요"),
    onError: () => toast("설정을 저장하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 회차 조작 — 금액 수정·건너뛰기·날짜 변경·미납.
 *
 * <p>손댄 회차만 서버에 1행이 남는다. 규칙을 고치는 것과 다른 일이다 — 규칙 수정은 앞으로의
 * 모든 회차를 바꾸고, 이건 <b>이번 회차만</b> 바꾼다.
 */
export function useOccurrenceAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: OccurrenceRequest) => patchOccurrence(body),
    onSuccess: (_, body) => toast(OCCURRENCE_MESSAGES[body.action]),
    onError: () => toast("회차를 고치지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

const OCCURRENCE_MESSAGES: Record<OccurrenceRequest["action"], string> = {
  AMOUNT: "이번 회차 금액을 고쳤어요",
  SKIP: "이번 회차를 건너뛰었어요",
  MOVE: "날짜를 옮겼어요",
  UNPAID: "미납으로 표시했어요",
  REVERTED: "자동 기록을 되돌렸어요",
};

/** 미납을 실제 출금일로 확정한다. 새 거래를 만들지 않고 그 회차를 되살려 옮긴다. */
export function useConfirmOccurrence() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: OccurrenceConfirmRequest) => confirmOccurrence(body),
    onSuccess: () => toast("실제 출금일로 확정했어요"),
    onError: () => toast("확정하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 결제 처리.
 *
 * <p><b>자동으로 적지 않는다</b>(§7.2) — 잔고 부족·리볼빙·선결제·연회비 때문에 실제 출금액을
 * 앱이 알 수 없다. 사람이 누르는 이 경로가 유일하다.
 */
export function usePayStatement() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      statementId,
      body,
    }: {
      statementId: number;
      body: StatementPayRequest;
    }) => payStatement(statementId, body),
    onError: () => toast("결제 처리를 하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function usePauseRecurring() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      from,
      to,
    }: {
      id: number;
      from: string;
      to?: string | null;
    }) => pauseRecurring(id, { from, to }),
    onSuccess: () => toast("일시 정지했어요"),
    onError: () => toast("일시 정지하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useResumeRecurring() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => resumeRecurring(id),
    onSuccess: () => toast("다시 시작했어요"),
    onError: () => toast("다시 시작하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 해지. 항목은 목록에 「종료됨」으로 남는다 — 연간 고정비 회고에 필요하다.
 *
 * <p>되돌린 건수를 그대로 알린다. 「2건을 되돌렸다」는 사람이 확인해야 하는 사실이다.
 */
export function useEndRecurring() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: RecurringEndRequest }) =>
      endRecurring(id, body),
    onSuccess: (result) => toast(result.message),
    onError: () => toast("해지하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function usePutBudget() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      period,
      body,
    }: {
      period: string;
      body: BudgetPutRequest;
    }) => putBudget(period, body),
    onSuccess: () => toast("예산을 저장했어요"),
    onError: () => toast("예산을 저장하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 카테고리 속성 — 고정/변동 · 실적 제외 · 결산 제외(`LDG-051`).
 *
 * <p>세금·보험료를 실적에서 빼는 규칙은 카드사마다 다르고 사람마다 다르다. 코드에 박으면
 * 누군가는 반드시 틀린 숫자를 본다 — 그래서 <b>카테고리의 속성</b>이다.
 */
export function useUpdateCategoryAttributes() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: number;
      body: CategoryAttributesRequest;
    }) => updateCategoryAttributes(id, body),
    onSuccess: () => toast("카테고리 속성을 저장했어요"),
    onError: () => toast("카테고리 속성을 저장하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/** 카드 실적 조건. 기준(승인·청구)은 카드마다 다르므로 카드에 붙는다(§7.6). */
export function useUpdateUsageGoal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      cardId,
      body,
    }: {
      cardId: number;
      body: UsageGoalRequest;
    }) => updateUsageGoal(cardId, body),
    onSuccess: () => toast("실적 조건을 저장했어요"),
    onError: () => toast("실적 조건을 저장하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/**
 * 가져오기 실행(`LDG-091`).
 *
 * <p>넣을 줄 번호를 그대로 보낸다 — 서버가 중복이라고 다시 거르지 않는다. 미리보기에서 본
 * 것과 결과가 달라지면 그 순간 미리보기가 거짓말이 된다.
 *
 * <p>파일 여러 장을 <b>한 요청으로</b> 보낸다. 파일마다 배치가 하나씩 생기고, 도중에
 * 실패하면 전부 물린다 — 절반만 들어간 상태로 끝나면 무엇을 다시 올려야 하는지 모른다.
 */
export function useExecuteImport() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      files,
      requests,
    }: {
      files: File[];
      requests: {
        assetId: number;
        mapping: ImportMapping;
        skipRows?: number;
        dateFormat?: string | null;
        /** 암호가 걸린 xlsx의 비밀번호. 저장하지 않고 이 요청에서만 쓴다. */
        password?: string;
        source: string;
        rowNumbers: number[];
      }[];
    }) => executeImport(files, requests),
    onSuccess: (result) => toast(`${result.inserted}건을 넣었어요`),
    onError: () => toast("가져오지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/** 배치 되돌리기(`LDG-093`). 그 배치로 들어온 행만 사라진다. */
export function useRevertImportBatch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => revertImportBatch(id),
    onSuccess: (result) => toast(`${result.reverted}건을 되돌렸어요`),
    onError: () => toast("되돌리지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useCreateAutoRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: {
      keyword: string;
      matchType: LedgerMatchType;
      categoryId: number;
    }) => createAutoRule(body),
    onSuccess: () => toast("규칙을 추가했어요"),
    onError: () => toast("규칙을 추가하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useUpdateAutoRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: number;
      body: { enabled?: boolean; categoryId?: number; keyword?: string };
    }) => updateAutoRule(id, body),
    onError: () => toast("규칙을 고치지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useDeleteAutoRule() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteAutoRule(id),
    onSuccess: () => toast("규칙을 지웠어요"),
    onError: () => toast("규칙을 지우지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useCreatePoint() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: {
      name: string;
      unit: string;
      balance?: number;
      expiresOn?: string | null;
      memo?: string | null;
    }) => createPoint(body),
    onSuccess: () => toast("포인트를 추가했어요"),
    onError: () => toast("포인트를 추가하지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useUpdatePoint() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: number;
      body: {
        balance?: number;
        expiresOn?: string | null;
        clearExpiry?: boolean;
      };
    }) => updatePoint(id, body),
    onError: () => toast("포인트를 고치지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useDeletePoint() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deletePoint(id),
    onSuccess: () => toast("포인트를 지웠어요"),
    onError: () => toast("포인트를 지우지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}
