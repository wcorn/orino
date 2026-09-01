import { Check, RotateCcw } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type {
  ImportBatchResult,
  ImportPreviewResponse,
} from "@/features/ledger/api/ledger";
import { analyzeImport, previewImport } from "@/features/ledger/api/ledger";
import { ImportFilePicker } from "@/features/ledger/components/ImportFilePicker";
import { ImportMappingStep } from "@/features/ledger/components/ImportMappingStep";
import { ImportPreviewStep } from "@/features/ledger/components/ImportPreviewStep";
import {
  useExecuteImport,
  useRevertImportBatch,
} from "@/features/ledger/hooks/useLedgerMutations";
import {
  useImportBatches,
  useLedgerAssets,
} from "@/features/ledger/hooks/useLedgerQueries";
import type { ImportFileState } from "@/features/ledger/lib/importSession";
import {
  newFileState,
  readFailure,
  rowKey,
  sameHeaders,
  sendFailure,
  toFileMapping,
} from "@/features/ledger/lib/importSession";
import { cn } from "@/lib/utils";

const STEPS = ["파일", "열 맞추기", "확인", "완료"] as const;

/**
 * 가져오기 `/ledger/import`(확정 명세 §12).
 *
 * <p><b>수동 입력을 대체하지 않는다.</b> 초기 이관과 월말 대사를 위한 도구다. 네 단계가
 * 각각 사람에게 무언가를 <b>보여준 뒤</b> 다음으로 넘어간다 — 파일 → 열 맞추기 → 확인 → 완료.
 *
 * <p><b>파일을 여러 장 받는다</b>(#1320). 은행이 내려주는 거래내역은 한 장이 아니다. 파일마다
 * 열을 따로 맞추고, 배치도 파일마다 하나씩 생긴다 — 아홉 장 중 한 장만 물릴 수 있어야 한다.
 *
 * <p><b>자동으로 병합하지 않는다</b>(`LDG-092`). 중복 후보는 경고로 알리고 체크를 꺼 둘 뿐,
 * 합치는 버튼이 없다 — 병합의 불투명함이 원장 신뢰를 깨뜨린다.
 */
export function LedgerImportPage() {
  const [files, setFiles] = useState<ImportFileState[]>([]);
  // 새로 고르는 파일에 함께 붙일 비밀번호. 저장하지는 않는다 — 화면을 벗어나면 사라지고,
  // 서버도 그 요청에서만 쓴다.
  const [password, setPassword] = useState("");
  const [stage, setStage] = useState<"pick" | "map" | "confirm">("pick");
  const [activeIndex, setActiveIndex] = useState(0);
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);
  // 넣을 줄. 중복 후보는 처음부터 꺼져 있고, 켜는 것은 사람이 정한다.
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const { data: assetList } = useLedgerAssets();
  const execute = useExecuteImport();

  const assets = (assetList?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => !asset.hidden);

  const step = execute.isSuccess
    ? 3
    : stage === "pick"
      ? 0
      : stage === "map"
        ? 1
        : 2;

  const patch = (key: string, next: Partial<ImportFileState>) =>
    setFiles((prev) =>
      prev.map((file) => (file.key === key ? { ...file, ...next } : file)),
    );

  /**
   * 파일 한 장을 읽어 본다.
   *
   * <p>실패해도 <b>그 파일을 목록에서 빼지 않는다</b>. 암호가 걸린 파일이면 비밀번호를 적고
   * 다시 읽어야 하는데, 조용히 사라지면 무엇이 빠졌는지 알 수 없다.
   */
  const analyze = async (state: ImportFileState) => {
    try {
      const result = await analyzeImport(
        state.file,
        state.password || undefined,
      );
      patch(state.key, {
        analysis: result,
        failure: null,
        busy: false,
        // 머리글이 몇 번째 줄인지는 서버가 찾아 준다. 은행 파일은 앞에 안내문이 붙어 와서
        // 1이 아니고, 그걸 사람이 세게 하면 한 칸씩 밀린 매핑이 나온다.
        skipRows: result.headerRow + 1,
      });
    } catch (error) {
      patch(state.key, {
        analysis: null,
        failure: readFailure(error),
        busy: false,
      });
    }
  };

  const onPick = (picked: File[]) => {
    const added = picked.map((file) => ({ ...newFileState(file), password }));
    setFiles((prev) => [...prev, ...added]);
    added.forEach((state) => void analyze(state));
  };

  const onRetry = (key: string) => {
    const state = files.find((file) => file.key === key);
    if (!state) {
      return;
    }
    patch(key, { busy: true, failure: null });
    void analyze(state);
  };

  /**
   * 설정을 <b>열 구성이 같은</b> 파일에만 퍼뜨린다.
   *
   * <p>열이 다른 파일에 씌우면 한 칸씩 밀린 줄이 「오류」가 아니라 그럴듯하게 틀린 줄로
   * 들어간다. 몇 장에 닿았는지 돌려주어 화면이 사람에게 알린다.
   */
  const copyToOthers = (key: string) => {
    const source = files.find((file) => file.key === key);
    if (!source) {
      return 0;
    }
    const targets = files.filter(
      (file) => file.key !== key && sameHeaders(file, source),
    );
    const keys = new Set(targets.map((file) => file.key));
    setFiles((prev) =>
      prev.map((file) =>
        keys.has(file.key)
          ? {
              ...file,
              assetId: source.assetId,
              skipRows: source.skipRows,
              mapping: { ...source.mapping },
            }
          : file,
      ),
    );
    return targets.length;
  };

  const onPreview = async () => {
    setBusy(true);
    setFailure(null);
    try {
      const result = await previewImport(
        files.map((file) => file.file),
        files.map(toFileMapping),
      );
      setPreview(result);
      // 중복 후보와 형식 오류를 빼고 켠다 — 사람이 다시 켜는 것은 언제나 할 수 있다.
      setChosen(
        new Set(
          result.files.flatMap((file) =>
            file.rows
              .filter(
                (row) =>
                  row.error === null &&
                  row.duplicateOf === null &&
                  row.duplicateOfRow === null,
              )
              .map((row) => rowKey(file.fileIndex, row.rowNumber)),
          ),
        ),
      );
      setStage("confirm");
    } catch (error) {
      setFailure(
        sendFailure(error, "미리 볼 수 없어요. 열 맞추기를 확인해 주세요."),
      );
    } finally {
      setBusy(false);
    }
  };

  const onExecute = () => {
    if (!preview) {
      return;
    }
    execute.mutate({
      files: files.map((file) => file.file),
      requests: files.map((file, index) => ({
        ...toFileMapping(file),
        source: file.source.trim() === "" ? "가져오기" : file.source.trim(),
        rowNumbers: (preview.files[index]?.rows ?? [])
          .filter((row) => chosen.has(rowKey(index, row.rowNumber)))
          .map((row) => row.rowNumber),
      })),
    });
  };

  const restart = () => {
    setFiles([]);
    setPassword("");
    setPreview(null);
    setChosen(new Set());
    setActiveIndex(0);
    setStage("pick");
    setFailure(null);
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

      {step === 0 && (
        <ImportFilePicker
          files={files}
          password={password}
          onPasswordChange={setPassword}
          onPick={onPick}
          onFilePasswordChange={(key, value) => patch(key, { password: value })}
          onRetry={onRetry}
          onRemove={(key) =>
            setFiles((prev) => prev.filter((file) => file.key !== key))
          }
          onNext={() => {
            setActiveIndex(0);
            setStage("map");
          }}
        />
      )}

      {step === 1 && (
        <ImportMappingStep
          files={files}
          activeIndex={Math.min(activeIndex, Math.max(files.length - 1, 0))}
          onSelect={setActiveIndex}
          assets={assets.map((asset) => ({ id: asset.id, name: asset.name }))}
          onChange={patch}
          onCopyToOthers={copyToOthers}
          busy={busy}
          onNext={() => void onPreview()}
          onBack={() => setStage("pick")}
        />
      )}

      {step === 2 && preview && (
        <ImportPreviewStep
          preview={preview}
          files={files}
          chosen={chosen}
          onToggle={(fileIndex, rowNumber) =>
            setChosen((prev) => {
              const next = new Set(prev);
              const key = rowKey(fileIndex, rowNumber);
              if (next.has(key)) {
                next.delete(key);
              } else {
                next.add(key);
              }
              return next;
            })
          }
          onSourceChange={(key, source) => patch(key, { source })}
          busy={execute.isPending}
          onExecute={onExecute}
          onBack={() => {
            setPreview(null);
            setStage("map");
          }}
        />
      )}

      {step === 3 && execute.data && (
        <DoneStep
          batches={execute.data.batches}
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

/** 4단계 — 완료. 파일마다 배치가 하나씩 생겼으므로 파일마다 한 줄로 알린다. */
function DoneStep({
  batches,
  inserted,
  skipped,
  onRestart,
}: {
  batches: ImportBatchResult[];
  inserted: number;
  skipped: number;
  onRestart: () => void;
}) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col items-center gap-3 rounded-xl p-8 ring-1">
      <Check className="text-success size-8" />
      <p className="text-heading font-semibold">
        {batches.length > 1
          ? `파일 ${batches.length}장에서 ${inserted}건을 넣었어요`
          : `${inserted}건을 넣었어요`}
      </p>
      <p className="text-muted-foreground text-[13px]">
        {skipped}건은 넣지 않았어요. 아래 이력에서 파일마다 따로 되돌릴 수
        있습니다.
      </p>
      {batches.length > 1 && (
        <ul className="text-muted-foreground flex flex-col gap-1 text-[13px]">
          {batches.map((batch) => (
            <li key={batch.batchId}>
              {batch.fileName ?? "이름 없는 파일"} — {batch.inserted}건
            </li>
          ))}
        </ul>
      )}
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
