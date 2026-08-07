import { Button } from "@/components/ui/button";

interface DragModeBarProps {
  onDone: () => void;
}

/**
 * 드래그 모드 안내 바. 모드에 들어와 있다는 사실 자체와, 순서를 바꾸면 무엇이 따라 바뀌는지를
 * 알린다 — 이동시간·알림이 조용히 재계산되면 사용자는 무엇 때문에 값이 달라졌는지 알 수 없다.
 */
export function DragModeBar({ onDone }: DragModeBarProps) {
  return (
    <div
      role="status"
      className="border-primary text-primary flex items-center justify-between gap-2 rounded-lg border border-dashed px-3 py-2 text-[13px]"
    >
      <span>드래그 모드 · 순서를 바꾸면 이동시간과 알림을 다시 계산해요</span>
      <Button variant="ghost" size="sm" onClick={onDone}>
        완료
      </Button>
    </div>
  );
}
