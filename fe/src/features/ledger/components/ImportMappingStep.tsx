import { Check, Copy } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ImportFileState } from "@/features/ledger/lib/importSession";
import {
  FIELDS,
  isMappingReady,
  sameHeaders,
} from "@/features/ledger/lib/importSession";
import { cn } from "@/lib/utils";

/**
 * 2단계 — 열 맞추기.
 *
 * 표본을 **옆에 두고** 고른다. 열 번호만 보고 맞추라고 하면 사람이 파일을 따로 열어
 * 세어야 하고, 그러다 한 칸씩 밀린다.
 *
 * **파일마다 따로 맞춘다**(#1320). 다만 같은 곳에서 받은 아홉 장에 같은 매핑을 아홉 번
 * 적는 것은 고문이라, 열 구성이 같은 파일에는 **한 번에 퍼뜨린다.**
 */
export function ImportMappingStep({
  files,
  activeIndex,
  onSelect,
  assets,
  onChange,
  onCopyToOthers,
  busy,
  onNext,
  onBack,
}: {
  files: ImportFileState[];
  activeIndex: number;
  onSelect: (index: number) => void;
  assets: { id: number; name: string }[];
  onChange: (key: string, patch: Partial<ImportFileState>) => void;
  /** 열 구성이 같은 파일에 이 파일의 설정을 퍼뜨린다. 몇 장에 닿았는지 돌려준다. */
  onCopyToOthers: (key: string) => number;
  busy: boolean;
  onNext: () => void;
  onBack: () => void;
}) {
  const [copied, setCopied] = useState<number | null>(null);
  const active = files[activeIndex];
  const analysis = active?.analysis;
  if (!active || !analysis) {
    return null;
  }

  const columnOptions = [
    { value: "", label: "없음" },
    ...analysis.headers.map((header, index) => ({
      value: String(index),
      label: `${index + 1}. ${header || "(이름 없음)"}`,
    })),
  ];
  const hasAmount =
    active.mapping.amount !== null ||
    active.mapping.inflow !== null ||
    active.mapping.outflow !== null;
  const twins = files.filter(
    (file) => file.key !== active.key && sameHeaders(file, active),
  ).length;
  const unready = files.filter((file) => !isMappingReady(file));

  return (
    <>
      {/* 파일이 한 장이면 탭이 아무것도 고르게 하지 않는다 — 자리만 차지한다. */}
      {files.length > 1 && (
        <div
          role="tablist"
          aria-label="가져올 파일"
          className="flex flex-wrap gap-1.5"
        >
          {files.map((file, index) => (
            <button
              key={file.key}
              type="button"
              role="tab"
              aria-selected={index === activeIndex}
              onClick={() => onSelect(index)}
              className={cn(
                "flex items-center gap-1.5 rounded-full px-3 py-1 text-[13px]",
                index === activeIndex
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground",
              )}
            >
              {/* 어느 파일이 아직 안 맞춰졌는지 탭에서 보여야 한 장을 빠뜨리지 않는다. */}
              {isMappingReady(file) && <Check className="size-3" />}
              <span className="max-w-[160px] truncate">{file.file.name}</span>
            </button>
          ))}
        </div>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-[13px] font-semibold">
          {active.file.name} — {analysis.totalRows}줄
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
              value={active.assetId === null ? "" : String(active.assetId)}
              onValueChange={(value) =>
                onChange(active.key, { assetId: Number(value) })
              }
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
          {/* labelId만 주면 label이 입력과 이어지지 않는다 — 읽어 주는 이름이 없어진다. */}
          <FormField label="건너뛸 머리글 줄 수" htmlFor="import-skip">
            <Input
              id="import-skip"
              inputMode="numeric"
              value={String(active.skipRows)}
              onChange={(event) =>
                onChange(active.key, {
                  skipRows: Math.max(Number(event.target.value) || 0, 0),
                })
              }
            />
          </FormField>

          {twins > 0 && (
            <div className="flex flex-wrap items-center gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setCopied(onCopyToOthers(active.key))}
              >
                <Copy className="size-4" />이 설정을 나머지 {twins}장에도
              </Button>
              {copied !== null && (
                <span className="text-muted-foreground text-[13px]">
                  {copied}장에 적용했어요
                </span>
              )}
            </div>
          )}
          {twins > 0 && (
            <p className="text-muted-foreground -mt-1 text-[13px]">
              열 구성이 같은 파일에만 닿아요. 열이 다른 파일에 씌우면 한 칸씩
              밀린 줄이 들어갑니다.
            </p>
          )}
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
                  active.mapping[field.key] === null
                    ? ""
                    : String(active.mapping[field.key])
                }
                onValueChange={(value) =>
                  onChange(active.key, {
                    mapping: {
                      ...active.mapping,
                      [field.key]: value === "" ? null : Number(value),
                    },
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

      {/* 어느 파일이 남았는지 이름으로 말한다 — 「미리 보기」가 왜 안 눌리는지 알아야 한다. */}
      {unready.length > 0 && files.length > 1 && (
        <Alert variant="info">
          <AlertTitle>
            아직 맞추지 않은 파일이 {unready.length}장 있어요
          </AlertTitle>
          <AlertDescription>
            <p>{unready.map((file) => file.file.name).join(" · ")}</p>
          </AlertDescription>
        </Alert>
      )}

      <div className="flex justify-between gap-2">
        <Button type="button" variant="ghost" onClick={onBack}>
          다른 파일 고르기
        </Button>
        <Button
          type="button"
          disabled={unready.length > 0 || busy}
          onClick={onNext}
        >
          미리 보기
        </Button>
      </div>
    </>
  );
}
