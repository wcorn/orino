import { Button, PageHeader } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 480 }}>
      <PageHeader
        title="학습 자료"
        actions={<Button size="sm">자료 추가</Button>}
      />
    </div>
  );
}

export function WithDescription() {
  return (
    <div style={{ width: 480 }}>
      <PageHeader title="주간 계획표" description="변경 시 자동 저장됩니다" />
    </div>
  );
}
