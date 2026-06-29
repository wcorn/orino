import { Button, DialogFooter } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 360 }}>
      <DialogFooter>
        <Button variant="ghost" size="sm">
          취소
        </Button>
        <Button size="sm">저장</Button>
      </DialogFooter>
    </div>
  );
}

export function WithDelete() {
  return (
    <div style={{ width: 360 }}>
      <DialogFooter className="justify-between">
        <Button variant="ghost" size="sm" className="text-destructive">
          삭제
        </Button>
        <div style={{ display: "flex", gap: 8 }}>
          <Button variant="ghost" size="sm">
            취소
          </Button>
          <Button size="sm">저장</Button>
        </div>
      </DialogFooter>
    </div>
  );
}
