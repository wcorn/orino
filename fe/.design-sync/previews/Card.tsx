import {
  Button,
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "orino-fe";

export function Default() {
  return (
    <Card style={{ maxWidth: 360 }}>
      <CardHeader>
        <CardTitle>학습 자료</CardTitle>
        <CardDescription>이번 주 복습 카드 12장</CardDescription>
        <CardAction>
          <Button size="sm" variant="ghost">
            관리
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        오늘 복습할 항목을 확인하고 바로 시작할 수 있어요.
      </CardContent>
      <CardFooter>
        <Button size="sm">복습 시작</Button>
      </CardFooter>
    </Card>
  );
}
