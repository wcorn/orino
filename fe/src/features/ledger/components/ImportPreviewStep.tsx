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
import type { ImportPreviewResponse } from "@/features/ledger/api/ledger";
import type { ImportFileState } from "@/features/ledger/lib/importSession";
import { rowKey } from "@/features/ledger/lib/importSession";
import { formatAmount } from "@/features/ledger/lib/money";

/**
 * 3단계 — 확인.
 *
 * **중복 후보는 꺼진 채로 온다**(`LDG-092`). 합치는 버튼은 없다 — 켜고 끄는 것이
 * 유일한 처리이고, 그 판단은 사람이 한다.
 *
 * **파일 경계를 살려 보여준다**(#1320). 줄 번호는 파일 안에서 세므로, 합쳐 놓으면 3번 줄이
 * 여러 개가 되어 어느 줄을 보고 있는지 알 수 없다.
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

      {preview.files.map((filePreview) => {
        const state = files[filePreview.fileIndex];
        return (
          <section
            key={filePreview.fileIndex}
            className="flex flex-col gap-2 border-t pt-4 first:border-t-0 first:pt-0"
          >
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div className="flex flex-col gap-0.5">
                <h2 className="text-[13px] font-semibold">
                  {filePreview.fileName ??
                    `${filePreview.fileIndex + 1}번째 파일`}
                </h2>
                <p className="text-muted-foreground text-[13px]">
                  {filePreview.totalRows}줄 · 중복 후보{" "}
                  {filePreview.duplicateCount}건 · 오류 {filePreview.errorCount}
                  건
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
                  {filePreview.rows.map((row) => (
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
                              」의 {row.duplicateOfRow.rowNumber}번째 줄과 같아
                              보여요
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
          </section>
        );
      })}

      <div className="flex flex-wrap items-center justify-end gap-2">
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
    </>
  );
}
