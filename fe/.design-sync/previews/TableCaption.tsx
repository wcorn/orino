import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableRow,
} from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 360 }}>
      <Table>
        <TableCaption>최근 7일 복습 현황</TableCaption>
        <TableBody>
          <TableRow>
            <TableCell>자료구조</TableCell>
            <TableCell>12장</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  );
}
