import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  applyTemplate,
  type AssetCreateRequest,
  type AssetUpdateRequest,
  attachReceipt,
  bulkCreateTransactions,
  createAsset,
  createReceiptUploadUrl,
  createTemplate,
  createTransaction,
  deleteTemplate,
  deleteTransaction,
  detachReceipt,
  duplicateTransaction,
  reconcileAsset,
  type ReconcileRequest,
  type SettingsUpdateRequest,
  type TemplateCreateRequest,
  type TransactionCreatedResponse,
  type TransactionCreateRequest,
  type TransactionUpdateRequest,
  updateAsset,
  updateSettings,
  updateTransaction,
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
