import { ConfirmDialog } from "orino-fe";

export function Default() {
  return (
    <ConfirmDialog
      open
      onOpenChange={() => {}}
      title="자료를 삭제할까요?"
      description="자료·노트·카드가 모두 삭제됩니다. 되돌릴 수 없어요."
      confirmLabel="삭제"
      destructive
      onConfirm={() => {}}
    />
  );
}
