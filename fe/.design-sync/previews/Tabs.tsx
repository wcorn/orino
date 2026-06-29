import { Tabs, TabsContent, TabsList, TabsTrigger } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 400 }}>
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">개요</TabsTrigger>
          <TabsTrigger value="cards">카드</TabsTrigger>
          <TabsTrigger value="stats">통계</TabsTrigger>
        </TabsList>
        <TabsContent value="overview">
          이번 주 복습 12건, 완료율 75%.
        </TabsContent>
        <TabsContent value="cards">복습할 카드 목록입니다.</TabsContent>
        <TabsContent value="stats">주간 통계입니다.</TabsContent>
      </Tabs>
    </div>
  );
}
