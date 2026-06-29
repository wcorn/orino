import {
  Button,
  Card,
  CardAction,
  CardDescription,
  CardHeader,
  CardTitle,
} from "orino-fe";

export function Default() {
  return (
    <Card style={{ maxWidth: 360 }}>
      <CardHeader>
        <CardTitle>주간 계획표</CardTitle>
        <CardDescription>이번 주 일정 12건</CardDescription>
        <CardAction>
          <Button size="sm" variant="ghost">
            편집
          </Button>
        </CardAction>
      </CardHeader>
    </Card>
  );
}
