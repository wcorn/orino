import { Dialog } from "@base-ui/react/dialog";
import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";

const POPUP_CLASS =
  "bg-background fixed top-1/2 left-1/2 z-50 max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0";

type DialogPopupProps = ComponentProps<typeof Dialog.Popup> & {
  className?: string;
};

/**
 * 공유 다이얼로그 본문(Popup). 중앙 정렬 + 진입/종료 애니메이션을 캡슐화하고,
 * 좁거나 짧은 뷰포트에서 넘치지 않도록 max-height·세로 스크롤·좌우 안전 여백을 보장한다.
 * 폭은 호출부에서 className(max-w-*)으로 지정한다.
 */
export function DialogPopup({ className, ...props }: DialogPopupProps) {
  return <Dialog.Popup className={cn(POPUP_CLASS, className)} {...props} />;
}
