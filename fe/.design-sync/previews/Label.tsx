import { Input, Label } from "orino-fe";

export function Default() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, width: 280 }}>
      <Label htmlFor="label-name">이름</Label>
      <Input id="label-name" placeholder="이름을 입력하세요" />
    </div>
  );
}
