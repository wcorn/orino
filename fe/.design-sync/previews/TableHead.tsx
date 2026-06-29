import { Table, TableHead, TableHeader, TableRow } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 360 }}>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>과목</TableHead>
            <TableHead>카드</TableHead>
            <TableHead>상태</TableHead>
          </TableRow>
        </TableHeader>
      </Table>
    </div>
  );
}
