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
import { useUpdateSettings } from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerAssets,
  useLedgerCategories,
  useLedgerSettings,
} from "@/features/ledger/hooks/useLedgerQueries";
import { LAST_DAY_OF_MONTH } from "@/features/ledger/lib/period";

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
          </section>

          <section className="flex flex-col gap-2">
            <h2 className="text-[13px] font-semibold">카테고리</h2>
            <p className="text-muted-foreground text-[13px]">
              지우면 보관 처리되고, 붙어 있던 내역은 그대로 남습니다.
            </p>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>대분류</TableHead>
                  <TableHead>소분류</TableHead>
                  <TableHead>종류</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(categories ?? [])
                  .filter((category) => !category.archived)
                  .map((category) => (
                    <TableRow key={category.id}>
                      <TableCell>{category.name}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {category.children.length === 0
                          ? "—"
                          : category.children
                              .map((child) => child.name)
                              .join(" · ")}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {FLOW_LABEL[category.flow]}
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </section>
        </>
      )}
    </div>
  );
}

const FLOW_LABEL = {
  EXPENSE: "지출",
  INCOME: "수입",
  TRANSFER: "이체",
} as const;
