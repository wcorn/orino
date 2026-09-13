import { ChevronDown, ChevronRight } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  ImportFilePreview,
  ImportPreviewResponse,
  ImportPreviewRow,
} from "@/features/ledger/api/ledger";
import type { ImportFileState } from "@/features/ledger/lib/importSession";
import { rowKey } from "@/features/ledger/lib/importSession";
import { formatAmount } from "@/features/ledger/lib/money";

type View = "all" | "included" | "excluded" | "duplicate" | "error";

const VIEWS: { value: View; label: string }[] = [
  { value: "all", label: "전체" },
  { value: "included", label: "넣을 줄" },
  { value: "excluded", label: "빠지는 줄" },
  { value: "duplicate", label: "중복 후보" },
  { value: "error", label: "오류" },
];

function isDuplicate(row: ImportPreviewRow) {
  return row.duplicateOf !== null || row.duplicateOfRow !== null;
}

function matches(view: View, row: ImportPreviewRow, included: boolean) {
  switch (view) {
    case "all":
      return true;
    case "included":
      return included;
    case "excluded":
      return !included;
    case "duplicate":
      return isDuplicate(row);
    case "error":
      return row.error !== null;
  }
}

/**
 * 처음 펼쳐 둘 파일. 한 장이면 그 장을 펴고, **여러 장이면 모두 접는다** — 머리에 건수가
 * 보이고, 빠지는 줄은 보기로 골라 볼 수 있다.
 *
 * <p>「중복·오류가 있는 파일만 편다」로는 안 된다. 은행 파일은 끝의 「합계」 줄이 늘 오류라
 * 아홉 장이 모두 펼쳐져, 수천 줄을 다 펴 둔 것과 같아진다(실제 국민은행 파일로 확인).
 */
function defaultOpen(files: ImportFilePreview[]) {
  return new Set(files.length === 1 ? [files[0].fileIndex] : []);
}

/**
 * 3단계 — 확인.
 *
 * **중복 후보는 꺼진 채로 온다**(`LDG-092`). 합치는 버튼은 없다 — 켜고 끄는 것이
 * 유일한 처리이고, 그 판단은 사람이 한다.
 *
 * **파일 경계를 살려 보여준다**(#1320). 줄 번호는 파일 안에서 세므로, 합쳐 놓으면 3번 줄이
 * 여러 개가 되어 어느 줄을 보고 있는지 알 수 없다.
 *
 * **빠지는 줄을 골라 본다**(#1383). 은행 파일 아홉 장이면 수천 줄이라, 다 펼쳐 두면 무엇이
 * 빠지는지는 끝까지 내려야 보인다. 보기는 **보여주는 방식만** 바꾼다 — 무엇이 들어가는지는
 * 여전히 체크가 정한다.
 */
export function ImportPreviewStep({
  preview,
  files,
  chosen,
  onToggle,
  onSourceChange,
  busy,
  onExecute,
  onBack,
}: {
  preview: ImportPreviewResponse;
  files: ImportFileState[];
  /** 켜 둔 줄. 열쇠는 `파일:줄`이다 — 줄 번호만으로는 파일을 가릴 수 없다. */
  chosen: Set<string>;
  onToggle: (fileIndex: number, rowNumber: number) => void;
  onSourceChange: (key: string, source: string) => void;
  busy: boolean;
  onExecute: () => void;
  onBack: () => void;
}) {
  const [view, setView] = useState<View>("all");
  const [open, setOpen] = useState(() => defaultOpen(preview.files));
  /*
    보기를 고른 순간 거기 든 줄. 체크를 바꿔도 줄이 **그 자리에 남게** 붙잡아 둔다 —
    「빠지는 줄」에서 켜는 순간 줄이 사라지면 무엇을 켰는지 확인할 수 없다.
  */
  const [pinned, setPinned] = useState<Set<string> | null>(null);

  const allRows = preview.files.flatMap((file) =>
    file.rows.map((row) => ({ fileIndex: file.fileIndex, row })),
  );
  const count = (target: View) =>
    allRows.filter(({ fileIndex, row }) =>
      matches(target, row, chosen.has(rowKey(fileIndex, row.rowNumber))),
    ).length;
  const excluded = count("excluded");

  const changeView = (next: View) => {
    setView(next);
    if (next === "all") {
      setPinned(null);
      setOpen(defaultOpen(preview.files));
      return;
    }
    const keys = new Set(
      allRows
        .filter(({ fileIndex, row }) =>
          matches(next, row, chosen.has(rowKey(fileIndex, row.rowNumber))),
        )
        .map(({ fileIndex, row }) => rowKey(fileIndex, row.rowNumber)),
    );
    setPinned(keys);
    // 그 보기에 해당하는 줄이 있는 파일만 편다. 없는 파일을 펴 봐야 빈 칸이다.
    setOpen(
      new Set(
        preview.files
          .filter((file) =>
            file.rows.some((row) =>
              keys.has(rowKey(file.fileIndex, row.rowNumber)),
            ),
          )
          .map((file) => file.fileIndex),
      ),
    );
  };

  const toggleOpen = (fileIndex: number) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(fileIndex)) {
        next.delete(fileIndex);
      } else {
        next.add(fileIndex);
      }
      return next;
    });

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
              이미 원장에 있는 거래뿐 아니라, <b>함께 올린 앞 파일의 줄</b>과
              겹치는 것도 찾습니다 — 기간이 겹치게 내려받은 파일을 함께 올렸을
              때 두 번 들어가는 것을 막기 위해서예요. 체크를 꺼 두었으니, 넣어야
              하는 줄은 직접 켜 주세요.
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

      {/* 내려가도 몇 건이 들어가고 몇 건이 빠지는지, 넣는 버튼이 늘 보이게 붙여 둔다. */}
      <div className="bg-background sticky top-0 z-10 flex flex-col gap-2 border-b py-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium tabular-nums">
            {`넣을 줄 ${chosen.size}건 · 빠지는 줄 ${excluded}건`}
          </p>
          <div className="flex flex-wrap items-center gap-2">
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
        {/* 탭 다섯 개가 좁은 화면을 가로로 밀어내지 않게 탭 줄만 스크롤한다. */}
        <div className="-mb-1 overflow-x-auto pb-1">
          <Tabs
            value={view}
            onValueChange={(value) => changeView(value as View)}
          >
            <TabsList className="w-max">
              {VIEWS.map((item) => (
                <TabsTrigger key={item.value} value={item.value}>
                  {`${item.label} ${count(item.value)}`}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>
      </div>

      {preview.files.map((filePreview) => {
        const state = files[filePreview.fileIndex];
        const isOpen = open.has(filePreview.fileIndex);
        const name =
          filePreview.fileName ?? `${filePreview.fileIndex + 1}번째 파일`;
        const included = filePreview.rows.filter((row) =>
          chosen.has(rowKey(filePreview.fileIndex, row.rowNumber)),
        ).length;
        const rows =
          pinned === null
            ? filePreview.rows
            : filePreview.rows.filter((row) =>
                pinned.has(rowKey(filePreview.fileIndex, row.rowNumber)),
              );

        return (
          <section
            key={filePreview.fileIndex}
            className="flex flex-col gap-2 border-t pt-4 first:border-t-0 first:pt-0"
          >
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div className="flex min-w-0 flex-col gap-0.5">
                <h2 className="text-[13px] font-semibold">
                  <button
                    type="button"
                    aria-expanded={isOpen}
                    onClick={() => toggleOpen(filePreview.fileIndex)}
                    className="flex items-start gap-1.5 text-left"
                  >
                    {isOpen ? (
                      <ChevronDown
                        aria-hidden
                        className="text-muted-foreground mt-0.5 size-4 shrink-0"
                      />
                    ) : (
                      <ChevronRight
                        aria-hidden
                        className="text-muted-foreground mt-0.5 size-4 shrink-0"
                      />
                    )}
                    <span className="break-all">{name}</span>
                  </button>
                </h2>
                {/* 접혀 있어도 이 파일에서 몇 건이 들어가는지는 보인다. */}
                <p className="text-muted-foreground text-[13px] tabular-nums">
                  {`${filePreview.totalRows}줄 · 넣을 줄 ${included} · 중복 후보 ${filePreview.duplicateCount} · 오류 ${filePreview.errorCount}`}
                </p>
              </div>
              {/* 배치는 파일마다 하나다 — 이름도 파일마다 따로 붙는다. */}
              {state && (
                <div className="flex w-[220px] flex-col gap-1.5">
                  <Label htmlFor={`source-${state.key}`}>
                    이 가져오기의 이름
                  </Label>
                  <Input
                    id={`source-${state.key}`}
                    value={state.source}
                    onChange={(event) =>
                      onSourceChange(state.key, event.target.value)
                    }
                    placeholder="신한카드 8월"
                  />
                </div>
              )}
            </div>

            {isOpen && rows.length === 0 && (
              <p className="text-muted-foreground text-[13px]">
                이 보기에 해당하는 줄이 없어요.
              </p>
            )}

            {isOpen && rows.length > 0 && (
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
                    {rows.map((row) => (
                      <TableRow key={row.rowNumber}>
                        <TableCell>
                          <Checkbox
                            checked={chosen.has(
                              rowKey(filePreview.fileIndex, row.rowNumber),
                            )}
                            disabled={row.error !== null}
                            aria-label={`${filePreview.fileName ?? ""} ${row.rowNumber}번째 줄 넣기`}
                            onChange={() =>
                              onToggle(filePreview.fileIndex, row.rowNumber)
                            }
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
                            {/* 어느 파일 몇 번째 줄인지 말해야 사람이 그 줄을 찾아 판단한다. */}
                            {row.duplicateOfRow !== null && (
                              <span className="text-muted-foreground text-[13px]">
                                「
                                {preview.files[row.duplicateOfRow.fileIndex]
                                  ?.fileName ??
                                  `${row.duplicateOfRow.fileIndex + 1}번째 파일`}
                                」의 {row.duplicateOfRow.rowNumber}번째 줄과
                                같아 보여요
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
            )}
          </section>
        );
      })}
    </>
  );
}
