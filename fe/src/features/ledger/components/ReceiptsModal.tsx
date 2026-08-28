import { Paperclip, Trash2 } from "lucide-react";
import { useRef } from "react";

import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { Modal } from "@/components/ui/modal";

import {
  useAttachReceipt,
  useDetachReceipt,
} from "../hooks/useLedgerMutations";
import { useLedgerReceipts } from "../hooks/useLedgerQueries";

interface ReceiptsModalProps {
  /** `null`이면 닫힌 상태. 열려 있는 거래의 id다. */
  transactionId: number | null;
  onClose: () => void;
  title: string;
}

/**
 * 영수증 첨부(`LDG-016`).
 *
 * <p>파일은 <b>BE를 거치지 않는다</b>. presigned URL을 받아 브라우저가 MinIO에 직접 PUT 하고,
 * 서버에는 키만 보낸다 — 일상기록과 같은 경로다.
 *
 * <p>떼어내도 오브젝트는 남는다. 실수로 지웠을 때 되돌릴 수 있어야 하기 때문이고,
 * 회수는 보존 배치의 몫이다.
 */
export function ReceiptsModal({
  transactionId,
  onClose,
  title,
}: ReceiptsModalProps) {
  const fileInput = useRef<HTMLInputElement>(null);
  const { data: receipts, isPending } = useLedgerReceipts(transactionId);
  const attach = useAttachReceipt();
  const detach = useDetachReceipt();

  return (
    <Modal
      open={transactionId !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title="영수증"
      description={title}
    >
      <div className="mt-4 flex flex-col gap-4">
        {isPending && transactionId !== null && <LoadingText />}

        {receipts && receipts.length > 0 && (
          <ul className="grid grid-cols-3 gap-2">
            {receipts.map((receipt) => (
              <li key={receipt.id} className="relative">
                {/* 확대는 새 탭이다 — 라이트박스를 만들면 이 화면에만 있는 상호작용이 하나 는다. */}
                <a
                  href={receipt.url}
                  target="_blank"
                  rel="noreferrer"
                  className="ring-border block overflow-hidden rounded-lg ring-1"
                >
                  {receipt.contentType?.startsWith("image/") ? (
                    <img
                      src={receipt.url}
                      alt="영수증"
                      className="aspect-square w-full object-cover"
                    />
                  ) : (
                    <span className="bg-muted text-muted-foreground flex aspect-square w-full items-center justify-center text-[13px]">
                      파일
                    </span>
                  )}
                </a>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  aria-label="영수증 떼기"
                  className="bg-background/80 absolute top-1 right-1"
                  onClick={() => detach.mutate(receipt.id)}
                >
                  <Trash2 className="size-3" />
                </Button>
              </li>
            ))}
          </ul>
        )}

        {receipts && receipts.length === 0 && (
          <p className="text-muted-foreground text-sm">
            아직 붙인 영수증이 없어요.
          </p>
        )}

        <input
          ref={fileInput}
          type="file"
          accept="image/*,application/pdf"
          aria-label="영수증 파일"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file && transactionId !== null) {
              attach.mutate({ transactionId, file });
            }
            event.target.value = "";
          }}
        />

        <Modal.Footer>
          <Button
            type="button"
            variant="outline"
            disabled={attach.isPending}
            onClick={() => fileInput.current?.click()}
            className="mr-auto"
          >
            <Paperclip className="size-4" />
            영수증 추가
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            닫기
          </Button>
        </Modal.Footer>
      </div>
    </Modal>
  );
}
