import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select } from "@/components/ui/select";

import type { AssetType, AssetView } from "../api/ledger";
import { useDeleteAsset, useUpdateAsset } from "../hooks/useLedgerMutations";
import { useLedgerAssets } from "../hooks/useLedgerQueries";

/** 잔액을 자기 이름으로 갖는 유형(BE `LedgerAssetType.holdsBalance`). 체크카드의 연결 후보다. */
const BALANCE_TYPES: AssetType[] = ["CASH", "CHECKING", "SAVINGS", "PREPAID"];

const NO_GROUP = "";

interface AssetEditModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  asset: AssetView;
}

/**
 * 자산 고치기 · 해지 · 삭제(`LDG-002`).
 *
 * <p>세 가지가 한 자리에 있는 이유는 <b>고르는 순간에 차이가 보여야</b> 하기 때문이다.
 * 「해지」는 자산을 목록에서 내리되 과거 내역을 그대로 두고, 「삭제」는 행 자체를 없앤다.
 * 그래서 삭제는 <b>아직 아무것도 붙지 않은 자산에만</b> 열린다 — 거래가 한 줄이라도 있으면
 * 서버가 `LDG-ERR-034`로 거부하고, 그때 필요한 것은 해지다.
 *
 * <p>유형은 바꾸지 않는다. 통장을 신용카드로 바꾸면 이미 쌓인 거래의 의미가 통째로 달라진다
 * — 잔액이던 것이 청구액이 된다. 그건 고치기가 아니라 다른 자산이다.
 */
export function AssetEditModal({
  open,
  onOpenChange,
  asset,
}: AssetEditModalProps) {
  const { data } = useLedgerAssets();
  const update = useUpdateAsset();
  const remove = useDeleteAsset();
  const navigate = useNavigate();

  const [name, setName] = useState(asset.name);
  const [groupId, setGroupId] = useState(
    asset.groupId === null ? NO_GROUP : String(asset.groupId),
  );
  const [accountLast4, setAccountLast4] = useState(asset.accountLast4 ?? "");
  const [linkedAssetId, setLinkedAssetId] = useState(
    asset.linkedAssetId === null ? "" : String(asset.linkedAssetId),
  );
  const [closedReason, setClosedReason] = useState("");
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const groups = (data?.groups ?? []).filter((group) => group.id !== null);
  const linkable = (data?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter(
      (item) => item.id !== asset.id && BALANCE_TYPES.includes(item.type),
    );

  const needsLink = asset.type === "DEBIT_CARD";
  const linkMissing = needsLink && linkedAssetId === "";
  const canSave = name.trim() !== "" && !linkMissing && !update.isPending;

  const close = () => {
    setConfirmingDelete(false);
    onOpenChange(false);
  };

  const save = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSave) {
      return;
    }
    update.mutate(
      {
        id: asset.id,
        body: {
          name: name.trim(),
          // 그룹만은 「비운다」와 「그대로 둔다」가 달라서 별도 플래그가 있다.
          ...(groupId === NO_GROUP
            ? { clearGroup: true }
            : { groupId: Number(groupId) }),
          accountLast4: accountLast4.trim(),
          linkedAssetId: needsLink ? Number(linkedAssetId) : null,
        },
      },
      { onSuccess: close },
    );
  };

  /** 해지·되살리기. 지우지 않는다 — 과거 내역이 갈 곳을 잃지 않아야 한다. */
  const toggleHidden = () => {
    update.mutate(
      {
        id: asset.id,
        body: asset.hidden
          ? { hidden: false, closedReason: "" }
          : { hidden: true, closedReason: closedReason.trim() || "해지" },
      },
      { onSuccess: close },
    );
  };

  const destroy = () => {
    remove.mutate(asset.id, {
      onSuccess: () => {
        close();
        // 방금 지운 자산의 상세에 남아 있으면 다음 화면은 404다.
        navigate("/ledger/assets");
      },
    });
  };

  return (
    <Modal
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title="자산 수정"
      description={`${asset.name} · 유형은 바꿀 수 없어요.`}
    >
      <form onSubmit={save} className="mt-4 flex flex-col gap-4">
        <FormField label="이름" htmlFor="ledger-asset-edit-name">
          <Input
            id="ledger-asset-edit-name"
            autoComplete="off"
            maxLength={60}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </FormField>

        {needsLink && (
          <FormField label="연결 계좌" labelId="ledger-asset-edit-linked">
            <Select
              value={linkedAssetId}
              onValueChange={setLinkedAssetId}
              options={[
                { value: "", label: "고르세요" },
                ...linkable.map((item) => ({
                  value: String(item.id),
                  label: item.name,
                })),
              ]}
              ariaLabelledby="ledger-asset-edit-linked"
              disabled={linkable.length === 0}
            />
          </FormField>
        )}

        {groups.length > 0 && (
          <FormField label="그룹" labelId="ledger-asset-edit-group">
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
              ariaLabelledby="ledger-asset-edit-group"
            />
          </FormField>
        )}

        <FormField label="계좌 끝 4자리" htmlFor="ledger-asset-edit-last4">
          <Input
            id="ledger-asset-edit-last4"
            inputMode="numeric"
            autoComplete="off"
            maxLength={4}
            value={accountLast4}
            onChange={(event) =>
              setAccountLast4(event.target.value.replace(/\D/g, "").slice(0, 4))
            }
            className="tabular-nums"
          />
        </FormField>

        <Modal.Footer>
          <Button type="button" variant="ghost" onClick={close}>
            취소
          </Button>
          <Button type="submit" disabled={!canSave}>
            저장
          </Button>
        </Modal.Footer>
      </form>

      {/* 되돌리기 어려운 일은 폼 밖에 둔다 — 저장하려다 손이 미끄러질 자리가 아니다. */}
      <section className="border-border mt-6 flex flex-col gap-3 border-t pt-4">
        {asset.hidden ? (
          <>
            <p className="text-muted-foreground text-[13px]">
              해지한 자산이에요. 되살리면 목록과 합계에 다시 들어옵니다.
            </p>
            <div>
              <Button type="button" variant="outline" onClick={toggleHidden}>
                되살리기
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="text-muted-foreground text-[13px]">
              <b>해지</b>는 목록에서만 내립니다 — 과거 내역은 그대로 남아요.
            </p>
            <div className="flex flex-wrap items-end gap-2">
              <FormField
                label="해지 사유"
                htmlFor="ledger-asset-closed-reason"
                className="min-w-[160px] flex-1"
              >
                <Input
                  id="ledger-asset-closed-reason"
                  autoComplete="off"
                  maxLength={30}
                  value={closedReason}
                  onChange={(event) => setClosedReason(event.target.value)}
                  placeholder="해지"
                />
              </FormField>
              <Button type="button" variant="outline" onClick={toggleHidden}>
                해지하기
              </Button>
            </div>
          </>
        )}

        <p className="text-muted-foreground text-[13px]">
          <b>삭제</b>는 아직 아무것도 적지 않은 자산에만 됩니다. 거래가 한
          줄이라도 있으면 서버가 막아요 — 그 내역이 갈 곳을 잃기 때문입니다.
        </p>
        {confirmingDelete ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[13px]">지우면 되돌릴 수 없어요.</span>
            <Button
              type="button"
              variant="destructive"
              disabled={remove.isPending}
              onClick={destroy}
            >
              지웁니다
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setConfirmingDelete(false)}
            >
              그만두기
            </Button>
          </div>
        ) : (
          <div>
            <Button
              type="button"
              variant="outline"
              onClick={() => setConfirmingDelete(true)}
            >
              삭제
            </Button>
          </div>
        )}
      </section>
    </Modal>
  );
}
