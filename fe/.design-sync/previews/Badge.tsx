import { Badge } from "orino-fe";

export function Variants() {
  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
      <Badge>기본</Badge>
      <Badge variant="secondary">보조</Badge>
      <Badge variant="success">완료</Badge>
      <Badge variant="warning">주의</Badge>
      <Badge variant="info">정보</Badge>
      <Badge variant="destructive">실패</Badge>
      <Badge variant="outline">아웃라인</Badge>
    </div>
  );
}
