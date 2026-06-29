import { Button, Modal } from "orino-fe";

export function Default() {
  return (
    <Modal open onOpenChange={() => {}} className="max-w-sm">
      <h2 className="text-base font-semibold">블록 편집</h2>
      <p className="text-muted-foreground mt-2 text-sm">
        시간과 라벨을 수정하세요.
      </p>
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="ghost" size="sm">
          취소
        </Button>
        <Button size="sm">저장</Button>
      </div>
    </Modal>
  );
}
