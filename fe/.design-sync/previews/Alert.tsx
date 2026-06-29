import { AlertTriangle, CheckCircle2, Info, XCircle } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "orino-fe";

export function Variants() {
  return (
    <div
      style={{ display: "flex", flexDirection: "column", gap: 12, width: 440 }}
    >
      <Alert variant="info">
        <Info />
        <AlertTitle>안내</AlertTitle>
        <AlertDescription>다음 복습은 내일입니다.</AlertDescription>
      </Alert>
      <Alert variant="success">
        <CheckCircle2 />
        <AlertTitle>완료</AlertTitle>
        <AlertDescription>변경 사항이 저장되었습니다.</AlertDescription>
      </Alert>
      <Alert variant="warning">
        <AlertTriangle />
        <AlertTitle>주의</AlertTitle>
        <AlertDescription>마감이 1시간 남았습니다.</AlertDescription>
      </Alert>
      <Alert variant="destructive">
        <XCircle />
        <AlertTitle>실패</AlertTitle>
        <AlertDescription>저장에 실패했습니다.</AlertDescription>
      </Alert>
    </div>
  );
}
