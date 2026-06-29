import { Table, TableBody, TableCell, TableRow } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 360 }}>
      <Table>
        <TableBody>
          <TableRow>
            <TableCell>자료구조</TableCell>
            <TableCell>12장</TableCell>
          </TableRow>
          <TableRow>
            <TableCell>알고리즘</TableCell>
            <TableCell>8장</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  );
}
