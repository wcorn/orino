import {
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "orino-fe";

export function Default() {
  return (
    <Card style={{ maxWidth: 360 }}>
      <CardHeader>
        <CardTitle>복습 시작</CardTitle>
      </CardHeader>
      <CardContent>오늘 복습할 카드 8장</CardContent>
      <CardFooter style={{ gap: 8 }}>
        <Button size="sm">시작</Button>
        <Button size="sm" variant="outline">
          나중에
        </Button>
      </CardFooter>
    </Card>
  );
}
