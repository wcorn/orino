import type { JSONContent } from "@tiptap/react";
import { UploadCloud } from "lucide-react";
import { useMemo, useRef, useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

import { createDatasetFromTable, shouldUseDataset } from "./datasetImport";
import { DEFAULT_IMPORT_SOURCE, IMPORT_SOURCES } from "./importers";
import type { SheetData } from "./sheetParser";
import {
  buildTableNode,
  MAX_COLS,
  MAX_ROWS,
  type NormalizedTable,
  wouldExceedNoteLimit,
} from "./tableContent";

const PREVIEW_ROWS = 5;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 현재 에디터 문서(삽입 시 1MB 초과 가드용). */
  currentDoc: unknown;
  /** 표 노드를 커서 위치에 삽입한다. */
  onInsert: (node: JSONContent) => void;
}

export function ImportDialog({
  open,
  onOpenChange,
  currentDoc,
  onInsert,
}: Props) {
  const [source, setSource] = useState(DEFAULT_IMPORT_SOURCE);
  const [fileName, setFileName] = useState<string | null>(null);
  const [sheets, setSheets] = useState<SheetData[] | null>(null);
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
    const accept = activeSource.accept;
    if (accept && !file.name.toLowerCase().endsWith(accept)) {
      setError(`${accept} 파일만 가져올 수 있어요.`);
      return;
    }
    if (!activeSource.parse) return;
    setError(null);
    setParsing(true);
    setFileName(file.name);
    try {
      const parsed = await activeSource.parse(file);
      const firstWithData = parsed.find((s) => s.rows.length > 0) ?? parsed[0];
      setSheets(parsed);
      setSelectedSheet(firstWithData?.name ?? "");
    } catch {
      setError("파일을 읽을 수 없어요. 손상되지 않은 .xlsx인지 확인해 주세요.");
      setSheets(null);
      setFileName(null);
    } finally {
      setParsing(false);
    }
  };

  const current = sheets?.find((s) => s.name === selectedSheet) ?? null;

  const normalized: NormalizedTable | null = useMemo(() => {
    if (!current || current.rows.length === 0) return null;
    if (firstRowAsHeader) {
      return { headers: current.rows[0], rows: current.rows.slice(1) };
    }
    return { headers: null, rows: current.rows };
  }, [current, firstRowAsHeader]);

  // 대형 표는 데이터셋 그리드 블록으로, 소형은 native Tiptap 표로 분기.
  const useDataset = useMemo(
    () =>
      normalized ? shouldUseDataset(normalized, MAX_COLS, MAX_ROWS) : false,
    [normalized],
  );

  const build = useMemo(
    () => (normalized && !useDataset ? buildTableNode(normalized) : null),
    [normalized, useDataset],
  );

  // native 경로만 노트 1MB 제한을 받는다(데이터셋은 참조 노드만이라 무관).
  const exceedsLimit = useMemo(
    () => (build ? wouldExceedNoteLimit(currentDoc, build.node) : false),
    [build, currentDoc],
  );

  const totalRows = normalized?.rows.length ?? 0;
  const totalCols = normalized
    ? Math.max(
        normalized.headers?.length ?? 0,
        ...normalized.rows.map((r) => r.length),
        0,
      )
    : 0;

  const isEmptySheet = current !== null && current.rows.length === 0;
  const canImport = normalized !== null && !exceedsLimit && !submitting;

  const handleImport = async () => {
    if (!normalized || submitting) return;
    if (useDataset) {
      setSubmitting(true);
      try {
        const datasetId = await createDatasetFromTable(normalized);
        onInsert({ type: "datasetTable", attrs: { datasetId } });
        close();
      } catch {
        setError("표를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
        setSubmitting(false);
      }
      return;
    }
    if (!build || exceedsLimit) return;
    onInsert(build.node);
    close();
  };

  return (
    <Modal
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title="데이터 가져오기"
      description="엑셀·CSV 파일을 표로 삽입합니다. 값만 가져오고 서식·수식은 무시해요."
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

          {normalized && !isEmptySheet && (
            <>
              <TablePreview
                headers={normalized.headers}
                rows={normalized.rows}
              />
              <p className="text-muted-foreground text-sm">
                총 {totalRows}행 × {totalCols}열
              </p>
              {useDataset && (
                <Alert variant="info">
                  <AlertDescription>
                    대용량 표라 데이터 그리드 블록으로 저장돼요(스크롤·편집
                    가능).
                  </AlertDescription>
                </Alert>
              )}
              {exceedsLimit && (
                <Alert variant="destructive">
                  <AlertDescription>
                    노트가 너무 커서 이 표를 넣을 수 없어요.
                  </AlertDescription>
                </Alert>
              )}
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
