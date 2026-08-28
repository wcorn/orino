import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select } from "@/components/ui/select";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";

import type {
  LedgerFlow,
  SuggestionView,
  TransactionCreateRequest,
} from "../api/ledger";
import { useCreateTransaction } from "../hooks/useLedgerMutations";
import {
  useFxRate,
  useLedgerAssets,
  useLedgerCategories,
  useLedgerSettings,
} from "../hooks/useLedgerQueries";
import { evaluate } from "../lib/calculator";
import { todayIso } from "../lib/period";
import { AmountField } from "./AmountField";
import { TitleAutocomplete } from "./TitleAutocomplete";

/** 마지막으로 쓴 자산. 「자산 기본값 = 최근 사용」은 기기마다 다른 값이라 로컬에 둔다. */
const LAST_ASSET_KEY = "orino.ledger.lastAssetId";

const FLOW_TABS: { value: LedgerFlow; label: string }[] = [
  { value: "EXPENSE", label: "지출" },
  { value: "INCOME", label: "수입" },
  { value: "TRANSFER", label: "이체" },
];

function readLastAsset(): number | null {
  try {
    const raw = localStorage.getItem(LAST_ASSET_KEY);
    return raw ? Number(raw) : null;
  } catch {
    // 시크릿 창·차단 설정에서는 접근 자체가 던진다. 기본값이 없을 뿐 입력은 그대로 된다.
    return null;
  }
}

function rememberLastAsset(id: number) {
  try {
    localStorage.setItem(LAST_ASSET_KEY, String(id));
  } catch {
    // 기억하지 못해도 저장은 이미 끝났다. 여기서 실패를 알릴 이유가 없다.
  }
}

interface TransactionModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * 거래 입력 — 어디서든 `N`.
 *
 * <p><b>숫자 입력 → `Tab` → `Enter`로 마우스 없이 끝난다</b>(`LDG-018`). 그래서 폼이고,
 * `Enter`가 곧 저장이다.
 *
 * <p>필수는 금액·날짜·자산 셋이다. <b>카테고리는 비워도 저장된다</b> — 기록을 막느니
 * 나중에 채운다(확정 명세 §4.2). 미분류 건은 대시보드에 「정리할 내역」으로 남는다.
 */
export function TransactionModal({
  open,
  onOpenChange,
}: TransactionModalProps) {
  const [flow, setFlow] = useState<LedgerFlow>("EXPENSE");
  const [expression, setExpression] = useState("");
  const [currency, setCurrency] = useState("KRW");
  const [rateOverride, setRateOverride] = useState<number | null>(null);
  const [occurredOn, setOccurredOn] = useState(todayIso());
  const [assetId, setAssetId] = useState<string>("");
  const [counterAssetId, setCounterAssetId] = useState<string>("");
  const [parentCategoryId, setParentCategoryId] = useState<string>("");
  const [childCategoryId, setChildCategoryId] = useState<string>("");
  const [title, setTitle] = useState("");
  const [memo, setMemo] = useState("");
  const [tags, setTags] = useState("");
  const [keepOpen, setKeepOpen] = useState(false);

  const { data: assetList } = useLedgerAssets(open);
  const { data: settings } = useLedgerSettings();
  const { data: categories } = useLedgerCategories(flow);
  const fx = useFxRate(currency === "KRW" ? null : currency);
  const create = useCreateTransaction();

  // 숨긴 자산은 고르게 두지 않는다 — 해지한 카드로 오늘 결제할 수는 없다.
  const assets = (assetList?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => !asset.hidden);

  const rate = rateOverride ?? fx.data?.rate ?? null;

  useEffect(() => {
    if (!open) {
      return;
    }
    // 열 때마다 기본값을 다시 잡는다. 어제 열어 둔 값이 남아 있으면 오늘 날짜가 틀린다.
    setOccurredOn(todayIso());
    setRateOverride(null);
  }, [open]);

  useEffect(() => {
    if (!open || assets.length === 0 || assetId !== "") {
      return;
    }
    const remembered = readLastAsset();
    const fallback = settings?.defaultAssetId ?? assets[0]?.id;
    const initial = assets.some((asset) => asset.id === remembered)
      ? remembered
      : fallback;
    if (initial != null) {
      setAssetId(String(initial));
    }
  }, [open, assets, assetId, settings?.defaultAssetId]);

  // 유형을 바꾸면 카테고리 세트가 통째로 갈린다 — 지출 카테고리를 단 채 수입이 될 수 없다.
  useEffect(() => {
    setParentCategoryId("");
    setChildCategoryId("");
  }, [flow]);

  const parents = categories ?? [];
  const children =
    parents.find((category) => String(category.id) === parentCategoryId)
      ?.children ?? [];

  const amount = evaluate(expression);
  const foreign = currency !== "KRW";
  // 외화인데 환율이 없으면 원화 금액을 그대로 적는다 — 저장을 막지 않는다.
  const canSave = amount !== null && amount > 0 && assetId !== "";

  const reset = () => {
    setExpression("");
    setTitle("");
    setMemo("");
    setTags("");
    setChildCategoryId("");
    setCurrency("KRW");
    setRateOverride(null);
  };

  const pickSuggestion = (suggestion: SuggestionView) => {
    setTitle(suggestion.title);
    if (expression === "") {
      setExpression(String(suggestion.amount));
    }
    setAssetId(String(suggestion.assetId));
    if (suggestion.categoryId != null) {
      const parent = parents.find(
        (category) =>
          category.id === suggestion.categoryId ||
          category.children.some((child) => child.id === suggestion.categoryId),
      );
      if (parent) {
        setParentCategoryId(String(parent.id));
        setChildCategoryId(
          parent.id === suggestion.categoryId
            ? ""
            : String(suggestion.categoryId),
        );
      }
    }
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSave || amount === null) {
      return;
    }
    const categoryId = childCategoryId || parentCategoryId;
    const body: TransactionCreateRequest = {
      type: flow,
      occurredOn,
      assetId: Number(assetId),
      counterAssetId:
        flow === "TRANSFER" && counterAssetId ? Number(counterAssetId) : null,
      categoryId: categoryId ? Number(categoryId) : null,
      title: title.trim() || null,
      memo: memo.trim() || null,
      tags: tags
        .split(",")
        .map((tag) => tag.trim())
        .filter(Boolean),
    };
    if (foreign && rate !== null) {
      // 원화 금액은 서버가 정한다. 화면이 계산한 값을 함께 보내면 둘이 갈릴 수 있다.
      body.fx = { currency, amount, rate };
    } else if (foreign) {
      // 환율을 못 구했다. 통화 정보를 붙이면 반쪽 근거가 되므로 원화로만 적는다.
      body.amount = amount;
    } else {
      body.amount = amount;
    }

    create.mutate(body, {
      onSuccess: () => {
        rememberLastAsset(Number(assetId));
        reset();
        if (!keepOpen) {
          onOpenChange(false);
        }
      },
    });
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      size="lg"
      title="거래 입력"
      description="숫자 입력 → Tab 이동 → Enter 저장. 마우스 없이 완결됩니다."
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
        <Tabs
          value={flow}
          onValueChange={(value) => setFlow(value as LedgerFlow)}
        >
          <TabsList className="w-full">
            {FLOW_TABS.map((tab) => (
              <TabsTrigger key={tab.value} value={tab.value} className="flex-1">
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <AmountField
          expression={expression}
          onExpressionChange={setExpression}
          currency={currency}
          onCurrencyChange={(next) => {
            setCurrency(next);
            setRateOverride(null);
          }}
          rate={rate}
          onRateChange={setRateOverride}
          rateLoading={fx.isFetching}
          rateReferenceDate={fx.data?.referenceDate ?? null}
        />

        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <FormField label="날짜" htmlFor="ledger-date">
            <Input
              id="ledger-date"
              type="date"
              value={occurredOn}
              onChange={(event) => setOccurredOn(event.target.value)}
            />
          </FormField>

          <FormField label="자산" labelId="ledger-asset-label">
            <Select
              value={assetId}
              onValueChange={setAssetId}
              ariaLabelledby="ledger-asset-label"
              options={[
                { value: "", label: "자산 선택" },
                ...assets.map((asset) => ({
                  value: String(asset.id),
                  label: asset.name,
                })),
              ]}
            />
          </FormField>

          {flow === "TRANSFER" ? (
            <FormField label="받는 자산" labelId="ledger-counter-label">
              <Select
                value={counterAssetId}
                onValueChange={setCounterAssetId}
                ariaLabelledby="ledger-counter-label"
                options={[
                  { value: "", label: "자산 선택" },
                  ...assets
                    .filter((asset) => String(asset.id) !== assetId)
                    .map((asset) => ({
                      value: String(asset.id),
                      label: asset.name,
                    })),
                ]}
              />
            </FormField>
          ) : (
            <>
              <FormField label="대분류" labelId="ledger-parent-label">
                <Select
                  value={parentCategoryId}
                  onValueChange={(value) => {
                    setParentCategoryId(value);
                    setChildCategoryId("");
                  }}
                  ariaLabelledby="ledger-parent-label"
                  options={[
                    // 미분류가 정상 경로다. 「없음」을 고를 수 있어야 되돌릴 수도 있다.
                    { value: "", label: "선택 안 함" },
                    ...parents.map((category) => ({
                      value: String(category.id),
                      label: category.name,
                    })),
                  ]}
                />
              </FormField>
              <FormField label="소분류" labelId="ledger-child-label">
                <Select
                  value={childCategoryId}
                  onValueChange={setChildCategoryId}
                  ariaLabelledby="ledger-child-label"
                  disabled={children.length === 0}
                  options={[
                    { value: "", label: "선택 안 함" },
                    ...children.map((category) => ({
                      value: String(category.id),
                      label: category.name,
                    })),
                  ]}
                />
              </FormField>
            </>
          )}
        </div>

        <TitleAutocomplete
          value={title}
          onChange={setTitle}
          onPick={pickSuggestion}
        />

        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <FormField label="태그" htmlFor="ledger-tags">
            <Input
              id="ledger-tags"
              autoComplete="off"
              value={tags}
              onChange={(event) => setTags(event.target.value)}
              placeholder="쉼표로 구분 — 회사, 점심"
            />
          </FormField>
          <FormField label="메모" htmlFor="ledger-memo">
            <Textarea
              id="ledger-memo"
              rows={1}
              value={memo}
              onChange={(event) => setMemo(event.target.value)}
            />
          </FormField>
        </div>

        <p className="text-muted-foreground text-[13px]">
          금액만 적고 저장해도 됩니다 — 미분류 건은 대시보드에 「정리할
          내역」으로 남습니다. 날짜를 미래로 잡으면 자동으로 예정으로
          저장됩니다.
        </p>

        <Modal.Footer>
          {/* 여러 건을 이어 적는 사람이 매번 `N`을 다시 누르지 않게 한다. */}
          <label className="text-muted-foreground mr-auto flex items-center gap-2 text-[13px]">
            <Checkbox
              checked={keepOpen}
              onChange={(event) => setKeepOpen(event.target.checked)}
            />
            저장 후 계속 입력
          </label>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button type="submit" disabled={!canSave || create.isPending}>
            저장
          </Button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
