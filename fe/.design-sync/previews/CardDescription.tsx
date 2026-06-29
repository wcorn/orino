import { Card, CardDescription, CardHeader, CardTitle } from "orino-fe";

export function Default() {
  return (
    <Card style={{ maxWidth: 360 }}>
      <CardHeader>
        <CardTitle>복습 카드</CardTitle>
        <CardDescription>
          오늘 복습할 카드 8장이 준비되어 있어요.
        </CardDescription>
      </CardHeader>
    </Card>
  );
}
