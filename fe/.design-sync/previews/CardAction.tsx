import {
  Button,
  Card,
  CardAction,
  CardHeader,
  CardTitle,
} from "orino-fe";

export function Default() {
  return (
    <Card style={{ maxWidth: 360 }}>
      <CardHeader>
        <CardTitle>알림 설정</CardTitle>
        <CardAction>
          <Button size="sm" variant="outline">
            관리
          </Button>
        </CardAction>
      </CardHeader>
    </Card>
  );
}
