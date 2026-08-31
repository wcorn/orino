import type { JSONContent } from "@tiptap/react";
import { UploadCloud } from "lucide-react";
import { useMemo, useRef, useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  analyzeImportFile,
  importSheetAsDataset,
  type ImportSheetSummary,
} from "@/features/note/dataset/api/datasets";
import { cn } from "@/lib/utils";
import { toast } from "@/shared/lib/toast";

import { DEFAULT_IMPORT_SOURCE, IMPORT_SOURCES } from "./importers";
import type { NormalizedTable } from "./tableContent";

const PREVIEW_ROWS = 5;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** datasetTable 노드를 커서 위치에 삽입한다. */
  onInsert: (node: JSONContent) => void;
}

export function ImportDialog({ open, onOpenChange, onInsert }: Props) {
  const [source, setSource] = useState(DEFAULT_IMPORT_SOURCE);
  const [fileName, setFileName] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [sheets, setSheets] = useState<ImportSheetSummary[] | null>(null);
  const [selectedSheet, setSelectedSheet] = useState<string>("");
  const [firstRowAsHeader, setFirstRowAsHeader] = useState(true);
  const [parsing, setParsing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const activeSource =
    IMPORT_SOURCES.find((s) => s.id === source) ?? IMPORT_SOURCES[0];

  const reset = () => {
    setFileName(null);
    setFile(null);
    setSheets(null);
    setSelectedSheet("");
    setFirstRowAsHeader(true);
    setParsing(false);
    setSubmitting(false);
    setError(null);
  };

  const close = () => {
    reset();
    onOpenChange(false);
  };

  const selectSource = (id: string) => {
    setSource(id);
    reset();
  };

  const handleFile = async (file: File) => {
    const accept = activeSource.accept ?? "";
    if (accept && !file.name.toLowerCase().endsWith(accept)) {
      setError(`${accept} 파일만 가져올 수 있어요.`);
      return;
    }
    setError(null);
    setParsing(true);
    setFileName(file.name);
    setFile(file);
    try {
      const parsed = await analyzeImportFile(file);
      const firstWithData = parsed.find((s) => s.rowCount > 0) ?? parsed[0];
      setSheets(parsed);
      setSelectedSheet(firstWithData?.name ?? "");
    } catch {
      setError(
        `파일을 읽을 수 없어요. 손상되지 않은 ${accept} 파일인지 확인해 주세요.`,
      );
      setSheets(null);
      setFileName(null);
      setFile(null);
    } finally {
      setParsing(false);
    }
  };

  const current = sheets?.find((s) => s.name === selectedSheet) ?? null;

  // 미리보기는 서버가 준 앞부분 몇 줄이다. 머리글 토글은 그 줄을 어떻게 <b>보여줄지</b>만
  // 가르고, 실제 해석은 가져올 때 서버가 같은 규칙으로 한다.
  const preview: NormalizedTable | null = useMemo(() => {
    if (!current || current.preview.length === 0) return null;
    if (firstRowAsHeader) {
      return { headers: current.preview[0], rows: current.preview.slice(1) };
    }
    return { headers: null, rows: current.preview };
  }, [current, firstRowAsHeader]);

  const totalRows = current
    ? Math.max(current.rowCount - (firstRowAsHeader ? 1 : 0), 0)
    : 0;
  const totalCols = current?.columnCount ?? 0;

  const isEmptySheet = current !== null && current.rowCount === 0;
  const canImport = preview !== null && !submitting;

  const handleImport = async () => {
    if (!file || !current || submitting) return;
    setSubmitting(true);
    try {
      const result = await importSheetAsDataset(
        file,
        current.name,
        firstRowAsHeader,
      );
      if (result.formulasAsValue > 0) {
        // 조용히 값으로 바꾸면 사람은 수식이 들어온 줄 안다 — 나중에 숫자가 안 따라
        // 움직이는 걸 보고서야 알게 된다.
        toast(
          `수식 ${result.formulasAsValue}개는 옮길 수 없어 값으로 들어왔어요.`,
          "info",
        );
      }
      onInsert({
        type: "datasetTable",
        attrs: { datasetId: result.datasetId },
      });
      close();
    } catch {
      setError("표를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title="데이터 가져오기"
      description="엑셀·CSV 파일을 표로 삽입합니다. 수식·서식·병합도 함께 가져와요."
      size="lg"
    >
      {/* 소스 선택 (v1: Excel만, 나머지 곧) */}
      <div className="mt-4 flex flex-wrap gap-2">
        {IMPORT_SOURCES.map((s) => (
          <button
            key={s.id}
            type="button"
            disabled={!s.available}
            aria-pressed={s.available && source === s.id}
            onClick={() => selectSource(s.id)}
            className={cn(
              "rounded-md border px-3 py-1.5 text-sm transition-colors",
              s.available && source === s.id
                ? "border-primary bg-primary/10 text-primary"
                : "border-border text-muted-foreground",
              !s.available && "cursor-not-allowed opacity-50",
            )}
          >
            {s.label}
            {!s.available && " · 곧"}
          </button>
        ))}
      </div>

      {/* 파일 드롭/선택 */}
      <div
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          const file = e.dataTransfer.files[0];
          if (file) void handleFile(file);
        }}
        className="border-border mt-4 flex flex-col items-center gap-2 rounded-lg border border-dashed p-6 text-center"
      >
        <UploadCloud className="text-muted-foreground size-6" />
        <p className="text-muted-foreground text-sm">
          {fileName ?? `${activeSource.accept} 파일을 끌어 놓거나 선택하세요`}
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => fileInputRef.current?.click()}
        >
          파일 선택
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          accept={activeSource.accept}
          aria-label="가져올 파일"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void handleFile(file);
            e.target.value = "";
          }}
        />
      </div>

      {parsing && (
        <p className="text-muted-foreground mt-3 text-sm">파싱 중…</p>
      )}

      {error && (
        <Alert variant="destructive" className="mt-3">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {sheets && !parsing && (
        <div className="mt-4 flex flex-col gap-3">
          {/* 시트 선택 (2개 이상) */}
          {sheets.length > 1 && (
            <div className="flex items-center gap-2">
              <span id="import-sheet-label" className="text-sm font-medium">
                시트
              </span>
              <Select
                value={selectedSheet}
                onValueChange={setSelectedSheet}
                options={sheets.map((s) => ({ value: s.name, label: s.name }))}
                ariaLabelledby="import-sheet-label"
              />
            </div>
          )}

          {/* 첫 행 머리글 토글 */}
          <label className="flex items-center gap-2 text-sm">
            <Switch
              checked={firstRowAsHeader}
              onCheckedChange={setFirstRowAsHeader}
            />
            첫 행을 머리글로
          </label>

          {isEmptySheet && (
            <Alert variant="warning">
              <AlertDescription>이 시트에는 데이터가 없어요.</AlertDescription>
            </Alert>
          )}

          {preview && !isEmptySheet && (
            <>
              <TablePreview headers={preview.headers} rows={preview.rows} />
              <p className="text-muted-foreground text-sm">
                총 {totalRows}행 × {totalCols}열
              </p>
              <Alert variant="info">
                <AlertDescription>
                  표는 데이터 그리드 블록으로 저장돼요(스크롤·편집 가능).
                </AlertDescription>
              </Alert>
            </>
          )}
        </div>
      )}

      <Modal.Footer
        submitLabel="표로 가져오기"
        onSubmit={handleImport}
        submitDisabled={!canImport}
        pending={submitting}
        pendingLabel="가져오는 중…"
      />
    </Modal>
  );
}

function TablePreview({
  headers,
  rows,
}: {
  headers: string[] | null;
  rows: string[][];
}) {
  const previewRows = rows.slice(0, PREVIEW_ROWS);
  return (
    <div className="max-h-56 overflow-auto rounded-md border">
      <table className="w-full border-collapse text-sm">
        {headers && (
          <thead>
            <tr>
              {headers.map((h, i) => (
                <th
                  key={i}
                  className="border-border bg-muted border px-2 py-1 text-left font-semibold"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
        )}
        <tbody>
          {previewRows.map((row, r) => (
            <tr key={r}>
              {row.map((cell, c) => (
                <td key={c} className="border-border border px-2 py-1">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
