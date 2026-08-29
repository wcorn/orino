import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { FormField } from "@/components/ui/form-field";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { CategoryView } from "@/features/ledger/api/ledger";
import {
  useUpdateCategoryAttributes,
  useUpdateSettings,
} from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerAssets,
  useLedgerCategories,
  useLedgerSettings,
} from "@/features/ledger/hooks/useLedgerQueries";
import { LAST_DAY_OF_MONTH } from "@/features/ledger/lib/period";

/**
 * 통계 기본 관점. 화면을 열 때 어느 쪽으로 그릴지만 정한다 —
 * 청구서·예정·곡선은 이 값과 무관하게 언제나 청구 기준이다(§10.1).
 */
const PERSPECTIVE_OPTIONS = [
  { value: "SPEND", label: "소비 기준 (쓴 날)" },
  { value: "BILLING", label: "청구 기준 (빠지는 날)" },
];

/** 카테고리의 비용 성격. 「안 정함」이 따로 있어야 변동비로 잘못 세지 않는다. */
const COST_TYPE_OPTIONS = [
  { value: "", label: "안 정함" },
  { value: "FIXED", label: "고정비" },
  { value: "VARIABLE", label: "변동비" },
];

/** 1~28과 말일. 29~31은 없는 달이 있어 고르게 두지 않는다. */
const MONTH_START_OPTIONS = [
  ...Array.from({ length: 28 }, (_, index) => ({
    value: String(index + 1),
    label: `${index + 1}일`,
  })),
  { value: String(LAST_DAY_OF_MONTH), label: "말일" },
];

/**
 * 설정 `/ledger/settings`.
 *
 * <p>월 시작일은 <b>예산 기간에만</b> 쓴다(확정 명세 §9). 카드 정산 사이클과 정기 항목 주기는
 * 여기에 영향받지 않는다 — 하나를 고쳤을 때 다른 것이 따라 움직이면 사용자가 결과를
 * 예측할 수 없다. 화면에도 그렇게 적어 둔다.
 */
export function LedgerSettingsPage() {
  const { data: settings, isPending, isError } = useLedgerSettings();
  const { data: assetList } = useLedgerAssets();
  const { data: categories } = useLedgerCategories();
  const update = useUpdateSettings();
  const updateAttributes = useUpdateCategoryAttributes();

  const assets = (assetList?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => !asset.hidden);

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-6">
      <PageHeader title="가계부 설정" />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">설정을 불러오지 못했어요.</Alert>
      )}

      {settings && (
        <>
          <section className="flex flex-col gap-4">
            <h2 className="text-[13px] font-semibold">기간과 기본값</h2>

            <FormField label="월 시작일" labelId="ledger-month-start">
              <Select
                value={String(settings.monthStartDay)}
                onValueChange={(value) =>
                  update.mutate({ monthStartDay: Number(value) })
                }
                ariaLabelledby="ledger-month-start"
                options={MONTH_START_OPTIONS}
              />
            </FormField>
            <p className="text-muted-foreground -mt-2 text-[13px]">
              예산 기간에만 쓰여요. 카드 결제일과 정기 항목 주기는 이 값에 따라
              움직이지 않습니다.
            </p>

            <label className="flex items-center justify-between gap-3">
              <span className="flex flex-col">
                <span className="text-sm font-medium">주말·공휴일 보정</span>
                <span className="text-muted-foreground text-[13px]">
                  시작일이 주말이면 직전 영업일로 당깁니다.
                </span>
              </span>
              <Switch
                checked={
                  settings.monthStartWeekendPolicy === "PREV_BUSINESS_DAY"
                }
                onCheckedChange={(checked) =>
                  update.mutate({
                    monthStartWeekendPolicy: checked
                      ? "PREV_BUSINESS_DAY"
                      : "AS_IS",
                  })
                }
              />
            </label>

            <FormField label="기본 자산" labelId="ledger-default-asset">
              <Select
                value={
                  settings.defaultAssetId === null
                    ? ""
                    : String(settings.defaultAssetId)
                }
                onValueChange={(value) =>
                  update.mutate(
                    value === ""
                      ? { clearDefaultAsset: true }
                      : { defaultAssetId: Number(value) },
                  )
                }
                ariaLabelledby="ledger-default-asset"
                options={[
                  { value: "", label: "정하지 않음" },
                  ...assets.map((asset) => ({
                    value: String(asset.id),
                    label: asset.name,
                  })),
                ]}
              />
            </FormField>
            <p className="text-muted-foreground -mt-2 text-[13px]">
              입력 모달이 처음 열릴 때 고를 자산이에요. 마지막으로 쓴 자산이
              있으면 그쪽이 먼저입니다.
            </p>

            <FormField
              label="통계 기본 관점"
              labelId="ledger-default-perspective"
            >
              <Select
                value={settings.defaultPerspective}
                onValueChange={(value) =>
                  update.mutate({
                    defaultPerspective: value as "SPEND" | "BILLING",
                  })
                }
                ariaLabelledby="ledger-default-perspective"
                options={PERSPECTIVE_OPTIONS}
              />
            </FormField>
            <p className="text-muted-foreground -mt-2 text-[13px]">
              통계 화면이 처음 열릴 때의 기준이에요. 청구서·예정·잔액 곡선은 이
              값과 상관없이 언제나 청구 기준입니다.
            </p>
          </section>

          <section className="flex flex-col gap-2">
            <h2 className="text-[13px] font-semibold">카테고리</h2>
            <p className="text-muted-foreground text-[13px]">
              지우면 보관 처리되고, 붙어 있던 내역은 그대로 남습니다. 여기서
              정한 속성이 고정/변동 추이 · 카드 실적 · 연간 결산에 그대로
              쓰입니다.
            </p>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>대분류</TableHead>
                  <TableHead>종류</TableHead>
                  <TableHead>비용 성격</TableHead>
                  <TableHead>실적 제외</TableHead>
                  <TableHead>결산 제외</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(categories ?? [])
                  .filter((category) => !category.archived)
                  .map((category) => (
                    <CategoryRow
                      key={category.id}
                      category={category}
                      onChange={(body) =>
                        updateAttributes.mutate({ id: category.id, body })
                      }
                    />
                  ))}
              </TableBody>
            </Table>
          </section>
        </>
      )}
    </div>
  );
}

/**
 * 카테고리 한 줄. <b>지출이 아닌 카테고리에는 속성 칸을 두지 않는다</b> —
 * 수입·이체에 「고정비」를 정할 수 있으면 어딘가에서 그 값이 지출로 세어진다.
 */
function CategoryRow({
  category,
  onChange,
}: {
  category: CategoryView;
  onChange: (body: {
    costType?: "FIXED" | "VARIABLE" | null;
    clearCostType?: boolean;
    excludeFromCardGoal?: boolean;
    excludeFromSettlement?: boolean;
  }) => void;
}) {
  const expense = category.flow === "EXPENSE";

  return (
    <TableRow>
      <TableCell>
        <span className="flex flex-col">
          {category.name}
          {category.children.length > 0 && (
            <span className="text-muted-foreground text-[13px]">
              {category.children.map((child) => child.name).join(" · ")}
            </span>
          )}
        </span>
      </TableCell>
      <TableCell className="text-muted-foreground">
        {FLOW_LABEL[category.flow]}
      </TableCell>
      {expense ? (
        <>
          <TableCell>
            <Select
              value={category.costType ?? ""}
              onValueChange={(value) =>
                // 「안 정함」은 값을 비우는 것이지 안 보내는 것이 아니다 —
                // null만 보내면 서버가 「안 건드림」으로 읽어 되돌릴 수 없다.
                onChange(
                  value === ""
                    ? { clearCostType: true }
                    : { costType: value as "FIXED" | "VARIABLE" },
                )
              }
              ariaLabel={`${category.name} 비용 성격`}
              options={COST_TYPE_OPTIONS}
            />
          </TableCell>
          <TableCell>
            <Switch
              checked={category.excludeFromCardGoal}
              onCheckedChange={(checked) =>
                onChange({ excludeFromCardGoal: checked })
              }
              aria-label={`${category.name} 실적 제외`}
            />
          </TableCell>
          <TableCell>
            <Switch
              checked={category.excludeFromSettlement}
              onCheckedChange={(checked) =>
                onChange({ excludeFromSettlement: checked })
              }
              aria-label={`${category.name} 결산 제외`}
            />
          </TableCell>
        </>
      ) : (
        <TableCell colSpan={3} className="text-muted-foreground text-[13px]">
          지출 카테고리만 속성을 갖습니다
        </TableCell>
      )}
    </TableRow>
  );
}

const FLOW_LABEL = {
  EXPENSE: "지출",
  INCOME: "수입",
  TRANSFER: "이체",
} as const;
