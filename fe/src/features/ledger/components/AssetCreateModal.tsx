import { useState } from "react";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select } from "@/components/ui/select";

import type { AssetType } from "../api/ledger";
import { useCreateAsset } from "../hooks/useLedgerMutations";
import { useLedgerAssets } from "../hooks/useLedgerQueries";

/** 유형 순서는 「많이 만드는 것」 순이다 — 통장이 맨 위, 선불이 맨 아래. */
const TYPE_OPTIONS: { value: AssetType; label: string }[] = [
  { value: "CHECKING", label: "입출금 통장" },
  { value: "SAVINGS", label: "예·적금" },
  { value: "CASH", label: "현금" },
  { value: "CREDIT_CARD", label: "신용카드" },
  { value: "DEBIT_CARD", label: "체크카드" },
  { value: "PREPAID", label: "선불·충전금" },
];

/** 잔액을 자기 이름으로 갖는 유형(BE `LedgerAssetType.holdsBalance`). 체크카드의 연결 후보다. */
const BALANCE_TYPES: AssetType[] = ["CASH", "CHECKING", "SAVINGS", "PREPAID"];

const NO_GROUP = "";

interface AssetCreateModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * 자산 만들기(`LDG-001`).
 *
 * <p><b>자산이 없으면 거래를 적을 수 없다</b>. 그런데 부트스트랩이 심는 것은 카테고리와
 * 설정뿐이라(D-14) 첫 자산은 사람이 만들어야 한다 — 그 자리가 이 모달이다.
 *
 * <p>체크카드의 <b>연결 계좌를 여기서 막는다</b>. 서버도 `LDG-ERR-019`로 거부하지만, 저장을
 * 누른 뒤에 듣는 것과 고르는 동안 아는 것은 다르다. 연결 계좌 없는 체크카드는 쓴 돈이
 * 어디서도 빠지지 않는 유령 자산이 된다(D-4).
 *
 * <p>잔액 칸은 두지 않는다. 잔액은 저장하는 값이 아니라 원장에서 파생하는 값이고(D-8),
 * 시작 잔액이 필요하면 자산 상세의 「잔액 맞추기」가 조정 거래로 남긴다 — 두 길을 두면
 * 하나는 원장을 거치지 않는 길이 된다.
 */
export function AssetCreateModal({
  open,
  onOpenChange,
}: AssetCreateModalProps) {
  const { data } = useLedgerAssets();
  const create = useCreateAsset();

  const [name, setName] = useState("");
  const [type, setType] = useState<AssetType>("CHECKING");
  const [groupId, setGroupId] = useState<string>(NO_GROUP);
  const [accountLast4, setAccountLast4] = useState("");
  const [linkedAssetId, setLinkedAssetId] = useState<string>("");

  const groups = (data?.groups ?? []).filter((group) => group.id !== null);
  // 숨긴 자산은 `hidden`에 따로 있어 후보에 들어오지 않는다 — 해지한 통장에 카드를 매달지 않는다.
  const linkable = (data?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => BALANCE_TYPES.includes(asset.type));

  const needsLink = type === "DEBIT_CARD";
  const linkMissing = needsLink && linkedAssetId === "";
  const canSubmit = name.trim() !== "" && !linkMissing && !create.isPending;

  const reset = () => {
    setName("");
    setType("CHECKING");
    setGroupId(NO_GROUP);
    setAccountLast4("");
    setLinkedAssetId("");
  };

  const close = () => {
    reset();
    onOpenChange(false);
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    create.mutate(
      {
        name: name.trim(),
        type,
        groupId: groupId === NO_GROUP ? null : Number(groupId),
        accountLast4: accountLast4.trim() === "" ? null : accountLast4.trim(),
        // 체크카드가 아니면 연결이라는 개념이 없다. 고른 값이 남아 있어도 보내지 않는다.
        linkedAssetId:
          needsLink && linkedAssetId !== "" ? Number(linkedAssetId) : null,
      },
      { onSuccess: close },
    );
  };

  return (
    <Modal
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title="자산 추가"
      description="자산이 있어야 거래를 적을 수 있어요."
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
        <FormField label="이름" htmlFor="ledger-asset-name">
          <Input
            id="ledger-asset-name"
            autoComplete="off"
            autoFocus
            maxLength={60}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="급여통장"
          />
        </FormField>

        <FormField label="유형" labelId="ledger-asset-type">
          <Select
            value={type}
            onValueChange={setType}
            options={TYPE_OPTIONS}
            ariaLabelledby="ledger-asset-type"
          />
        </FormField>

        {needsLink && (
          <FormField label="연결 계좌" labelId="ledger-asset-linked">
            <Select
              value={linkedAssetId}
              onValueChange={setLinkedAssetId}
              options={[
                { value: "", label: "고르세요" },
                ...linkable.map((asset) => ({
                  value: String(asset.id),
                  label: asset.name,
                })),
              ]}
              ariaLabelledby="ledger-asset-linked"
              disabled={linkable.length === 0}
            />
          </FormField>
        )}

        {needsLink && (
          <p className="text-muted-foreground text-[13px]">
            {linkable.length === 0
              ? "연결할 계좌가 없어요. 입출금 통장을 먼저 만들어 주세요."
              : "체크카드는 잔액을 갖지 않아요 — 쓴 돈은 연결 계좌에서 빠집니다."}
          </p>
        )}

        {type === "CREDIT_CARD" && (
          <p className="text-muted-foreground text-[13px]">
            결제일·마감일은 만든 뒤 카드 화면에서 정합니다. 정하기 전까지는
            사용액이 청구서로 묶이지 않아요.
          </p>
        )}

        {groups.length > 0 && (
          <FormField label="그룹" labelId="ledger-asset-group">
            <Select
              value={groupId}
              onValueChange={setGroupId}
              options={[
                { value: NO_GROUP, label: "그룹 없음" },
                ...groups.map((group) => ({
                  value: String(group.id),
                  label: group.name,
                })),
              ]}
              ariaLabelledby="ledger-asset-group"
            />
          </FormField>
        )}

        <FormField label="계좌 끝 4자리" htmlFor="ledger-asset-last4">
          <Input
            id="ledger-asset-last4"
            inputMode="numeric"
            autoComplete="off"
            maxLength={4}
            value={accountLast4}
            onChange={(event) =>
              setAccountLast4(event.target.value.replace(/\D/g, "").slice(0, 4))
            }
            placeholder="선택 — 같은 은행 통장이 여럿일 때 구분됩니다"
            className="tabular-nums"
          />
        </FormField>

        <Modal.Footer>
          <Button type="button" variant="ghost" onClick={close}>
            취소
          </Button>
          <Button type="submit" disabled={!canSubmit}>
            만들기
          </Button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
