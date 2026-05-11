import { PartyPopper } from "lucide-react";

export function EmptyTodayState() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20">
      <PartyPopper className="text-primary size-10" />
      <p className="text-foreground text-base font-medium">
        오늘 처리할 복습이 없어요.
      </p>
      <p className="text-muted-foreground text-sm">잘 하고 있어요!</p>
    </div>
  );
}
