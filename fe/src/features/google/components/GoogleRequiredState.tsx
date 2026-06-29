import { Dialog } from "@base-ui/react/dialog";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";

import { GoogleConnectButton } from "./GoogleConnectButton";

interface GoogleRequiredStateProps {
  /** 안내 문구(기본: "Google 연결이 필요합니다."). */
  message?: ReactNode;
}

/**
 * 다이얼로그에서 Google 미연동 시 보여주는 본문 — 안내 문구 + 닫기/연결 푸터.
 * 일정·할 일·루틴 폼이 동일하게 사용한다.
 */
export function GoogleRequiredState({
  message = "Google 연결이 필요합니다.",
}: GoogleRequiredStateProps) {
  return (
    <div className="mt-4 flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">{message}</p>
      <Modal.Footer className="mt-0">
        <Dialog.Close
          render={
            <Button variant="ghost" type="button">
              닫기
            </Button>
          }
        />
        <GoogleConnectButton />
      </Modal.Footer>
    </div>
  );
}
