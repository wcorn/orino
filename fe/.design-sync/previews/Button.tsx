import { Button } from "orino-fe";

export function Variants() {
  return (
    <div
      style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}
    >
      <Button>기본</Button>
      <Button variant="secondary">보조</Button>
      <Button variant="outline">아웃라인</Button>
      <Button variant="ghost">고스트</Button>
      <Button variant="destructive">삭제</Button>
      <Button variant="link">링크</Button>
    </div>
  );
}

export function Sizes() {
  return (
    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
      <Button size="sm">작게</Button>
      <Button>기본</Button>
      <Button size="lg">크게</Button>
    </div>
  );
}

export function Disabled() {
  return <Button disabled>비활성</Button>;
}
