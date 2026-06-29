import {
  Badge,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 480 }}>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>과목</TableHead>
            <TableHead>카드</TableHead>
            <TableHead>상태</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>자료구조</TableCell>
            <TableCell>12장</TableCell>
            <TableCell>
              <Badge variant="success">완료</Badge>
            </TableCell>
          </TableRow>
          <TableRow>
            <TableCell>알고리즘</TableCell>
            <TableCell>8장</TableCell>
            <TableCell>
              <Badge variant="warning">진행</Badge>
            </TableCell>
          </TableRow>
          <TableRow>
            <TableCell>운영체제</TableCell>
            <TableCell>5장</TableCell>
            <TableCell>
              <Badge variant="info">예정</Badge>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  );
}
