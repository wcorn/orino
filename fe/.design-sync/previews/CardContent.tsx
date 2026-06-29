import { Card, CardContent, CardHeader, CardTitle } from "orino-fe";

export function Default() {
  return (
    <Card style={{ maxWidth: 360 }}>
      <CardHeader>
        <CardTitle>오늘의 루틴</CardTitle>
      </CardHeader>
      <CardContent>
        아침 스트레칭 · 영어 단어 30개 · 알고리즘 1문제
      </CardContent>
    </Card>
  );
}
