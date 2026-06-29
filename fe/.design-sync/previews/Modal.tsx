import { Modal } from "orino-fe";

export function Default() {
  return (
    <Modal
      open
      onOpenChange={() => {}}
      title="블록 편집"
      description="시간과 라벨을 수정하세요."
      size="sm"
    >
      <Modal.Footer submitLabel="저장" onSubmit={() => {}} />
    </Modal>
  );
}
