import { Button, Menu, MenuItem } from "orino-fe";

export function Default() {
  return (
    <Menu
      trigger={
        <Button variant="outline" size="sm">
          메뉴 열기
        </Button>
      }
    >
      <MenuItem>편집</MenuItem>
      <MenuItem variant="destructive">삭제</MenuItem>
    </Menu>
  );
}
