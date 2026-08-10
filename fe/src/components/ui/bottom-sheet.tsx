import { Dialog } from "@base-ui/react/dialog";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

// 스크림은 fade만 150ms. Modal과 달리 blur를 걸지 않는다 — 시트는 화면 하단만 덮으므로
// 뒤 내용이 그대로 보이는 편이 "어디에서 여는 시트인지" 알기 쉽다.
const BACKDROP_CLASS =
  "fixed inset-0 z-50 bg-black/50 transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0";

// 하단 정렬 + translateY(100%)→0, 180ms ease-out. 이 이상의 커스텀 모션은 두지 않는다.
const POPUP_CLASS = [
  "bg-background fixed inset-x-0 bottom-0 z-50 mx-auto w-full max-w-[520px]",
  "max-h-[calc(100dvh-3rem)] overflow-y-auto rounded-t-xl border-t px-4 pt-3 pb-6 shadow-lg",
  "transition-transform duration-[180ms] ease-out",
  "data-[starting-style]:translate-y-full data-[ending-style]:translate-y-full",
].join(" ");

interface BottomSheetProps {
  open: boolean;
  /**
   * 열림 상태 변경. `reason`은 무엇이 닫았는지다(`outside-press`·`escape-key`…) — 롱프레스로
   * 연 시트처럼 <b>여는 손짓의 끝이 곧바로 바깥 클릭이 되는</b> 경우를 호출부가 가려낸다.
   */
  onOpenChange: (open: boolean, reason?: string) => void;
  /** 제목 — Dialog.Title로 렌더(접근성 필수). */
  title: ReactNode;
  /** 제목 아래 보조 설명(선택). */
  description?: ReactNode;
  /** 폭 미세조정용 추가 className(선택). */
  className?: string;
  children?: ReactNode;
}

/**
 * 하단에서 올라오는 시트. 모바일에서 한 손으로 고르는 선택지를 담는다
 * (날짜 담기·이동수단·추가 메뉴).
 *
 * <p>`Modal`과 같은 base-ui `Dialog`를 쓰므로 포커스 트랩·ESC·스크롤 락·`aria-modal`은
 * 그대로 따라온다. 다른 것은 정렬(하단)과 진입 모션(아래→위)뿐이다.
 */
export function BottomSheet({
  open,
  onOpenChange,
  title,
  description,
  className,
  children,
}: BottomSheetProps) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(next, details) => onOpenChange(next, details.reason)}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className={BACKDROP_CLASS} />
        <Dialog.Popup className={cn(POPUP_CLASS, className)}>
          {/* 잡아끄는 손잡이 모양 — 드래그로 닫는 제스처는 넣지 않는다(스크롤과 충돌한다). */}
          <div
            aria-hidden="true"
            className="bg-border mx-auto mb-3 h-1 w-9 rounded-full"
          />
          <Dialog.Title className="text-heading font-semibold">
            {title}
          </Dialog.Title>
          {description && (
            <Dialog.Description className="text-muted-foreground text-label mt-1">
              {description}
            </Dialog.Description>
          )}
          <div className="mt-4">{children}</div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
