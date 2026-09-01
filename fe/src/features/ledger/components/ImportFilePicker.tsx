import { FileText, Upload, X } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingText } from "@/components/ui/loading-text";
import type { ImportFileState } from "@/features/ledger/lib/importSession";

/**
 * 1단계 — 파일 고르기.
 *
 * **여러 장을 한 번에 고른다**(#1320). 은행이 내려주는 거래내역은 한 장이 아니다 — 기간을
 * 나눠 받아야 하고, 아홉 해치가 아홉 장으로 온다.
 *
 * 고른 파일은 **목록으로 남는다.** 파일마다 읽힌 줄 수나 못 읽은 이유가 제 줄에 붙어야,
 * 한 장만 암호가 걸렸을 때 어느 장인지 알 수 있다.
 */
export function ImportFilePicker({
  files,
  password,
  onPasswordChange,
  onPick,
  onFilePasswordChange,
  onRetry,
  onRemove,
  onNext,
}: {
  files: ImportFileState[];
  /** 새로 고르는 파일에 함께 붙일 비밀번호. 같은 곳에서 받은 파일들은 비밀번호도 같다. */
  password: string;
  onPasswordChange: (value: string) => void;
  onPick: (files: File[]) => void;
  onFilePasswordChange: (key: string, value: string) => void;
  onRetry: (key: string) => void;
  onRemove: (key: string) => void;
  onNext: () => void;
}) {
  const busy = files.some((file) => file.busy);
  const unread = files.filter((file) => !file.busy && !file.analysis);

  return (
    <>
      <section className="bg-card ring-foreground/10 flex flex-col items-center gap-3 rounded-xl p-8 ring-1">
        <Upload className="text-muted-foreground size-8" />
        <p className="text-sm font-medium">
          CSV 또는 .xlsx 파일을 고르세요 — 여러 장을 한 번에 고를 수 있어요
        </p>
        <p className="text-muted-foreground text-[13px]">
          카드사 명세서 · 은행 거래내역 · 다른 가계부 앱의 내보내기 파일
        </p>

        <div className="flex w-full max-w-[320px] flex-col gap-1.5">
          <Label htmlFor="import-password">파일 비밀번호</Label>
          <Input
            id="import-password"
            type="password"
            autoComplete="off"
            value={password}
            disabled={busy}
            onChange={(event) => onPasswordChange(event.target.value)}
            placeholder="암호가 걸린 파일만"
          />
          <p className="text-muted-foreground text-[13px]">
            은행 거래내역은 보통 암호가 걸려 있어요. 여기 적어 두면 지금 고르는
            파일에 함께 씁니다. 저장하지 않아요.
          </p>
        </div>

        <Label htmlFor="import-file" className="sr-only">
          가져올 파일
        </Label>
        <Input
          id="import-file"
          type="file"
          multiple
          accept=".csv,.xlsx,.txt"
          disabled={busy}
          className="max-w-[320px]"
          onChange={(event) => {
            const picked = [...(event.target.files ?? [])];
            if (picked.length > 0) {
              onPick(picked);
            }
            // 값을 비워 둔다 — 같은 파일을 다시 고르는 것도 「고른 것」이어야 한다.
            event.target.value = "";
          }}
        />
      </section>

      {files.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-[13px] font-semibold">
            고른 파일 {files.length}장
          </h2>
          <ul className="flex flex-col gap-1.5">
            {files.map((file) => (
              <li
                key={file.key}
                className="bg-card ring-foreground/10 flex flex-col gap-2 rounded-lg p-3 ring-1"
              >
                <div className="flex items-center gap-2">
                  <FileText className="text-muted-foreground size-4 shrink-0" />
                  <span className="min-w-0 flex-1 truncate text-sm">
                    {file.file.name}
                  </span>
                  {file.busy && <LoadingText />}
                  {file.analysis && (
                    <span className="text-muted-foreground text-[13px] tabular-nums">
                      {file.analysis.totalRows}줄
                    </span>
                  )}
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    aria-label={`${file.file.name} 목록에서 빼기`}
                    disabled={file.busy}
                    onClick={() => onRemove(file.key)}
                  >
                    <X className="size-4" />
                  </Button>
                </div>

                {/* 실패한 파일만 비밀번호를 다시 받는다 — 아홉 장 모두에게 묻지 않는다. */}
                {file.failure && (
                  <div className="flex flex-col gap-1.5">
                    <p className="text-destructive text-[13px]">
                      {file.failure}
                    </p>
                    <div className="flex items-end gap-2">
                      <div className="flex flex-col gap-1.5">
                        <Label htmlFor={`password-${file.key}`}>
                          이 파일의 비밀번호
                        </Label>
                        <Input
                          id={`password-${file.key}`}
                          type="password"
                          autoComplete="off"
                          className="max-w-[200px]"
                          value={file.password}
                          onChange={(event) =>
                            onFilePasswordChange(file.key, event.target.value)
                          }
                        />
                      </div>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => onRetry(file.key)}
                      >
                        다시 읽기
                      </Button>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>

          {/* 못 읽은 파일을 그냥 지나치면 사람은 전부 들어갔다고 믿는다. */}
          {unread.length > 0 && (
            <Alert variant="warning">
              <AlertTitle>읽지 못한 파일이 {unread.length}장 있어요</AlertTitle>
              <AlertDescription>
                <p>
                  비밀번호를 적고 다시 읽거나, 목록에서 빼 주세요. 그대로 두면
                  넣지 못한 채로 지나갑니다.
                </p>
              </AlertDescription>
            </Alert>
          )}

          <div className="flex justify-end">
            <Button
              type="button"
              disabled={busy || files.length === 0 || unread.length > 0}
              onClick={onNext}
            >
              열 맞추기
            </Button>
          </div>
        </section>
      )}
    </>
  );
}
