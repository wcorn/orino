import { Check, RotateCcw, Upload } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { EmptyState } from "@/components/ui/empty-state";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type {
  ImportAnalyzeResponse,
  ImportMapping,
  ImportPreviewResponse,
} from "@/features/ledger/api/ledger";
import { analyzeImport, previewImport } from "@/features/ledger/api/ledger";
import {
  useExecuteImport,
  useRevertImportBatch,
} from "@/features/ledger/hooks/useLedgerMutations";
import {
  useImportBatches,
  useLedgerAssets,
} from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

/** 매핑할 수 있는 자리. 날짜와 금액만 필수고 나머지는 비워 둘 수 있다. */
const FIELDS = [
  { key: "date", label: "날짜", required: true },
  { key: "amount", label: "금액", required: false },
  { key: "inflow", label: "입금", required: false },
  { key: "outflow", label: "출금", required: false },
  { key: "title", label: "내용", required: false },
  { key: "memo", label: "메모", required: false },
  { key: "type", label: "유형", required: false },
  { key: "category", label: "카테고리", required: false },
  { key: "asset", label: "자산", required: false },
] as const;

type FieldKey = (typeof FIELDS)[number]["key"];

const STEPS = ["파일", "열 맞추기", "확인", "완료"] as const;

/**
 * 가져오기 `/ledger/import`(확정 명세 §12).
 *
 * <p><b>수동 입력을 대체하지 않는다.</b> 초기 이관과 월말 대사를 위한 도구다. 네 단계가
 * 각각 사람에게 무언가를 <b>보여준 뒤</b> 다음으로 넘어간다 — 파일 → 열 맞추기 → 확인 → 완료.
 *
 * <p><b>자동으로 병합하지 않는다</b>(`LDG-092`). 중복 후보는 경고로 알리고 체크를 꺼 둘 뿐,
 * 합치는 버튼이 없다 — 병합의 불투명함이 원장 신뢰를 깨뜨린다.
 */
export function LedgerImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const [analysis, setAnalysis] = useState<ImportAnalyzeResponse | null>(null);
  const [assetId, setAssetId] = useState<number | null>(null);
  const [source, setSource] = useState("");
  const [skipRows, setSkipRows] = useState(1);
  const [mapping, setMapping] = useState<Record<FieldKey, number | null>>({
    date: null,
    amount: null,
    inflow: null,
    outflow: null,
    title: null,
    memo: null,
    type: null,
    category: null,
    asset: null,
  });
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);
  // 넣을 줄. 중복 후보는 처음부터 꺼져 있고, 켜는 것은 사람이 정한다.
  const [chosen, setChosen] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const { data: assetList } = useLedgerAssets();
  const execute = useExecuteImport();

  const assets = (assetList?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => !asset.hidden);

  const step = !analysis ? 0 : !preview ? 1 : execute.isSuccess ? 3 : 2;

  const onPickFile = async (picked: File) => {
    setBusy(true);
    setFailure(null);
    try {
      const result = await analyzeImport(picked);
      setFile(picked);
      setAnalysis(result);
      setSource(picked.name.replace(/\.[^.]+$/, ""));
      setPreview(null);
    } catch {
      setFailure("이 파일은 읽을 수 없어요. CSV 또는 .xlsx만 됩니다.");
    } finally {
      setBusy(false);
    }
  };

  const onPreview = async () => {
    if (!file || assetId === null) {
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      const result = await previewImport(file, {
        assetId,
        skipRows,
        mapping: mapping as ImportMapping,
      });
      setPreview(result);
      // 중복 후보와 형식 오류를 빼고 켠다 — 사람이 다시 켜는 것은 언제나 할 수 있다.
      setChosen(
        new Set(
          result.rows
            .filter((row) => row.error === null && row.duplicateOf === null)
            .map((row) => row.rowNumber),
        ),
      );
    } catch {
      setFailure("미리 볼 수 없어요. 열 맞추기를 확인해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const onExecute = () => {
    if (!file || assetId === null) {
      return;
    }
    execute.mutate({
      file,
      request: {
        assetId,
        skipRows,
        mapping: mapping as ImportMapping,
        source: source.trim() === "" ? "가져오기" : source.trim(),
        rowNumbers: [...chosen],
      },
    });
  };

  const restart = () => {
    setFile(null);
    setAnalysis(null);
    setPreview(null);
    setChosen(new Set());
    execute.reset();
  };

  return (
    <div className="mx-auto flex max-w-[960px] flex-col gap-5">
      <PageHeader title="가져오기" />

      <Stepper current={step} />

      <Alert variant="info">
        <AlertTitle>손으로 적는 것을 대신하지 않아요</AlertTitle>
        <AlertDescription>
          <p>
            처음 옮겨 올 때와 월말에 맞춰 볼 때 쓰는 도구예요. 넣기 전에 무엇이
            들어갈지 한 줄씩 보여 드립니다.
          </p>
        </AlertDescription>
      </Alert>

      {failure && <Alert variant="destructive">{failure}</Alert>}

      {step === 0 && <FilePicker busy={busy} onPick={onPickFile} />}

      {step === 1 && analysis && (
        <MappingStep
          analysis={analysis}
          assets={assets.map((asset) => ({ id: asset.id, name: asset.name }))}
          assetId={assetId}
          onAssetChange={setAssetId}
          skipRows={skipRows}
          onSkipRowsChange={setSkipRows}
          mapping={mapping}
          onMappingChange={setMapping}
          busy={busy}
          onNext={() => void onPreview()}
          onBack={restart}
        />
      )}

      {step === 2 && preview && (
        <PreviewStep
          preview={preview}
          chosen={chosen}
          onToggle={(rowNumber) =>
            setChosen((prev) => {
              const next = new Set(prev);
              if (next.has(rowNumber)) {
                next.delete(rowNumber);
              } else {
                next.add(rowNumber);
              }
              return next;
            })
          }
          source={source}
          onSourceChange={setSource}
          busy={execute.isPending}
          onExecute={onExecute}
          onBack={() => setPreview(null)}
        />
      )}

      {step === 3 && execute.data && (
        <DoneStep
          inserted={execute.data.inserted}
          skipped={execute.data.skipped}
          onRestart={restart}
        />
      )}

      <BatchHistory />
    </div>
  );
}

/** 지금 어디쯤인가. 네 단계가 보이면 「얼마나 남았나」를 묻지 않게 된다. */
function Stepper({ current }: { current: number }) {
  return (
    <ol className="flex flex-wrap items-center gap-2 text-[13px]">
      {STEPS.map((label, index) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={cn(
              "text-caption flex size-5 items-center justify-center rounded-full font-medium",
              index < current && "bg-primary text-primary-foreground",
              index === current && "bg-primary text-primary-foreground",
              index > current && "bg-muted text-muted-foreground",
            )}
          >
            {index < current ? <Check className="size-3" /> : index + 1}
          </span>
          <span
            className={
              index === current ? "font-medium" : "text-muted-foreground"
            }
          >
            {label}
          </span>
          {index < STEPS.length - 1 && (
            <span aria-hidden className="text-muted-foreground">
              ›
            </span>
          )}
        </li>
      ))}
    </ol>
  );
}

function FilePicker({
  busy,
  onPick,
}: {
  busy: boolean;
  onPick: (file: File) => void;
}) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col items-center gap-3 rounded-xl p-8 ring-1">
      <Upload className="text-muted-foreground size-8" />
      <p className="text-sm font-medium">CSV 또는 .xlsx 파일을 고르세요</p>
      <p className="text-muted-foreground text-[13px]">
        카드사 명세서 · 은행 거래내역 · 다른 가계부 앱의 내보내기 파일
      </p>
      <Label htmlFor="import-file" className="sr-only">
        가져올 파일
      </Label>
      <Input
        id="import-file"
        type="file"
        accept=".csv,.xlsx,.txt"
        disabled={busy}
        className="max-w-[320px]"
        onChange={(event) => {
          const picked = event.target.files?.[0];
          if (picked) {
            onPick(picked);
          }
        }}
      />
      {busy && <LoadingText />}
    </section>
  );
}

/**
 * 2단계 — 열 맞추기.
 *
 * <p>표본을 <b>옆에 두고</b> 고른다. 열 번호만 보고 맞추라고 하면 사람이 파일을 따로 열어
 * 세어야 하고, 그러다 한 칸씩 밀린다.
 */
function MappingStep({
  analysis,
  assets,
  assetId,
  onAssetChange,
  skipRows,
  onSkipRowsChange,
  mapping,
  onMappingChange,
  busy,
  onNext,
  onBack,
}: {
  analysis: ImportAnalyzeResponse;
  assets: { id: number; name: string }[];
  assetId: number | null;
  onAssetChange: (id: number) => void;
  skipRows: number;
  onSkipRowsChange: (rows: number) => void;
  mapping: Record<FieldKey, number | null>;
  onMappingChange: (mapping: Record<FieldKey, number | null>) => void;
  busy: boolean;
  onNext: () => void;
  onBack: () => void;
}) {
  const columnOptions = [
    { value: "", label: "없음" },
    ...analysis.headers.map((header, index) => ({
      value: String(index),
      label: `${index + 1}. ${header || "(이름 없음)"}`,
    })),
  ];
  const hasAmount =
    mapping.amount !== null ||
    mapping.inflow !== null ||
    mapping.outflow !== null;
  const ready = mapping.date !== null && hasAmount && assetId !== null;

  return (
    <>
      <section className="flex flex-col gap-3">
        <h2 className="text-[13px] font-semibold">
          파일 미리보기 — {analysis.totalRows}줄
        </h2>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                {analysis.headers.map((header, index) => (
                  <TableHead key={index}>
                    {index + 1}. {header || "(이름 없음)"}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {analysis.sample.map((row, rowIndex) => (
                <TableRow key={rowIndex}>
                  {analysis.headers.map((_, index) => (
                    <TableCell key={index} className="text-muted-foreground">
                      {row[index] ?? ""}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <section className="grid items-start gap-4 md:grid-cols-2">
        <div className="flex flex-col gap-3">
          <h2 className="text-[13px] font-semibold">어느 자산에 넣나요</h2>
          <FormField label="자산" labelId="import-asset">
            <Select
              value={assetId === null ? "" : String(assetId)}
              onValueChange={(value) => onAssetChange(Number(value))}
              ariaLabelledby="import-asset"
              options={[
                { value: "", label: "고르세요" },
                ...assets.map((asset) => ({
                  value: String(asset.id),
                  label: asset.name,
                })),
              ]}
            />
          </FormField>
          <p className="text-muted-foreground -mt-1 text-[13px]">
            자산 열을 맞추면 줄마다 그 이름으로 찾아가고, 못 찾은 줄만 여기로
            와요.
          </p>
          <FormField label="건너뛸 머리글 줄 수" labelId="import-skip">
            <Input
              id="import-skip"
              inputMode="numeric"
              value={String(skipRows)}
              onChange={(event) =>
                onSkipRowsChange(Math.max(Number(event.target.value) || 0, 0))
              }
            />
          </FormField>
        </div>

        <div className="flex flex-col gap-2">
          <h2 className="text-[13px] font-semibold">열 맞추기</h2>
          {FIELDS.map((field) => (
            <div
              key={field.key}
              className="flex items-center justify-between gap-3"
            >
              <span className="text-sm">
                {field.label}
                {field.required && (
                  <span className="text-destructive ml-1">*</span>
                )}
              </span>
              <Select
                value={
                  mapping[field.key] === null ? "" : String(mapping[field.key])
                }
                onValueChange={(value) =>
                  onMappingChange({
                    ...mapping,
                    [field.key]: value === "" ? null : Number(value),
                  })
                }
                ariaLabel={`${field.label} 열`}
                options={columnOptions}
              />
            </div>
          ))}
          {!hasAmount && (
            <p className="text-muted-foreground text-[13px]">
              금액, 또는 입금·출금 중 하나는 맞춰야 해요.
            </p>
          )}
        </div>
      </section>

      <div className="flex justify-between gap-2">
        <Button type="button" variant="ghost" onClick={onBack}>
          다른 파일 고르기
        </Button>
        <Button type="button" disabled={!ready || busy} onClick={onNext}>
          미리 보기
        </Button>
      </div>
    </>
  );
}

/**
 * 3단계 — 확인.
 *
 * <p><b>중복 후보는 꺼진 채로 온다</b>(`LDG-092`). 합치는 버튼은 없다 — 켜고 끄는 것이
 * 유일한 처리이고, 그 판단은 사람이 한다.
 */
function PreviewStep({
  preview,
  chosen,
  onToggle,
  source,
  onSourceChange,
  busy,
  onExecute,
  onBack,
}: {
  preview: ImportPreviewResponse;
  chosen: Set<number>;
  onToggle: (rowNumber: number) => void;
  source: string;
  onSourceChange: (source: string) => void;
  busy: boolean;
  onExecute: () => void;
  onBack: () => void;
}) {
  return (
    <>
      {preview.duplicateCount > 0 && (
        <Alert variant="warning">
          <AlertTitle>
            중복 후보 {preview.duplicateCount}건 — 자동으로 병합하지 않습니다
          </AlertTitle>
          <AlertDescription>
            <p>
              날짜·금액·내용·자산이 같은 건을 찾아 <b>보여 드릴 뿐</b>이에요.
              체크를 꺼 두었으니, 넣어야 하는 줄은 직접 켜 주세요.
            </p>
          </AlertDescription>
        </Alert>
      )}

      {preview.errorCount > 0 && (
        <Alert variant="destructive">
          <AlertTitle>읽지 못한 줄 {preview.errorCount}건</AlertTitle>
          <AlertDescription>
            <p>
              이 줄들은 넣을 수 없어요. 사유를 보고 파일을 고치거나 열 맞추기를
              다시 확인해 주세요.
            </p>
          </AlertDescription>
        </Alert>
      )}

      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>넣기</TableHead>
              <TableHead>줄</TableHead>
              <TableHead>날짜</TableHead>
              <TableHead>내용</TableHead>
              <TableHead>자산</TableHead>
              <TableHead>카테고리</TableHead>
              <TableHead>금액</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {preview.rows.map((row) => (
              <TableRow key={row.rowNumber}>
                <TableCell>
                  <Checkbox
                    checked={chosen.has(row.rowNumber)}
                    disabled={row.error !== null}
                    aria-label={`${row.rowNumber}번째 줄 넣기`}
                    onChange={() => onToggle(row.rowNumber)}
                  />
                </TableCell>
                <TableCell className="text-muted-foreground tabular-nums">
                  {row.rowNumber}
                </TableCell>
                <TableCell className="tabular-nums">
                  {row.occurredOn ?? "—"}
                </TableCell>
                <TableCell>
                  <span className="flex flex-col">
                    {row.title ?? "제목 없음"}
                    {row.error && (
                      <span className="text-destructive text-[13px]">
                        {row.error}
                      </span>
                    )}
                    {row.duplicateOf !== null && (
                      <span className="text-muted-foreground text-[13px]">
                        이미 있는 거래와 같아 보여요
                      </span>
                    )}
                  </span>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {row.assetName ?? "—"}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {row.categoryName ?? "미분류"}
                </TableCell>
                <TableCell className="tabular-nums">
                  {row.amount === null ? "—" : formatAmount(row.amount)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex w-[220px] flex-col gap-1.5">
          <Label htmlFor="import-source">이 가져오기의 이름</Label>
          <Input
            id="import-source"
            value={source}
            onChange={(event) => onSourceChange(event.target.value)}
            placeholder="신한카드 8월"
          />
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="ghost" onClick={onBack}>
            열 다시 맞추기
          </Button>
          <Button
            type="button"
            disabled={chosen.size === 0 || busy}
            onClick={onExecute}
          >
            {chosen.size}건 넣기
          </Button>
        </div>
      </div>
    </>
  );
}

function DoneStep({
  inserted,
  skipped,
  onRestart,
}: {
  inserted: number;
  skipped: number;
  onRestart: () => void;
}) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col items-center gap-3 rounded-xl p-8 ring-1">
      <Check className="text-success size-8" />
      <p className="text-heading font-semibold">{inserted}건을 넣었어요</p>
      <p className="text-muted-foreground text-[13px]">
        {skipped}건은 넣지 않았어요. 아래 이력에서 통째로 되돌릴 수 있습니다.
      </p>
      <Button type="button" variant="outline" onClick={onRestart}>
        다른 파일 가져오기
      </Button>
    </section>
  );
}

/**
 * 가져오기 이력(`LDG-093`).
 *
 * <p><b>되돌린 배치도 남는다</b> — 「무엇을 넣었다가 물렀는지」도 이력이고, 지우면 같은
 * 파일을 또 넣는 날 알 수 없다.
 */
function BatchHistory() {
  const { data, isPending } = useImportBatches();
  const revert = useRevertImportBatch();

  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-[13px] font-semibold">가져오기 이력</h2>
      <p className="text-muted-foreground text-[13px]">
        되돌리면 <b>그 가져오기로 들어온 줄만</b> 사라져요. 손으로 적은 내역은
        그대로 남습니다.
      </p>

      {isPending && <LoadingText />}
      {data && data.length === 0 && (
        <EmptyState className="min-h-[20svh]">
          <p className="text-muted-foreground text-sm">
            아직 가져온 파일이 없어요.
          </p>
        </EmptyState>
      )}

      {data && data.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>이름</TableHead>
              <TableHead>파일</TableHead>
              <TableHead>넣은 줄</TableHead>
              <TableHead>언제</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((batch) => (
              <TableRow key={batch.id}>
                <TableCell>
                  <span className="flex items-center gap-2">
                    {batch.source}
                    {batch.revertedAt && (
                      <Badge variant="outline">되돌림</Badge>
                    )}
                  </span>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {batch.fileName ?? "—"}
                </TableCell>
                <TableCell className="tabular-nums">
                  {batch.insertedCount} / {batch.rowCount}
                </TableCell>
                <TableCell className="text-muted-foreground tabular-nums">
                  {batch.createdAt.slice(0, 10)}
                </TableCell>
                <TableCell>
                  {/* 이미 되돌린 배치는 버튼이 없다 — 두 번째는 거부되고, 그 거부를
                      화면에서 만나는 것보다 누를 수 없는 편이 낫다. */}
                  {!batch.revertedAt && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      disabled={revert.isPending}
                      onClick={() => revert.mutate(batch.id)}
                    >
                      <RotateCcw className="size-4" />
                      되돌리기
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </section>
  );
}
