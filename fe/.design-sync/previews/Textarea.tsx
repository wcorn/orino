import { Textarea } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 320 }}>
      <Textarea
        defaultValue={"오늘 공부한 내용을 정리해 보세요.\n- 자료구조 1장\n- 알고리즘 복습"}
        rows={4}
      />
    </div>
  );
}

export function Placeholder() {
  return (
    <div style={{ width: 320 }}>
      <Textarea placeholder="메모를 입력하세요" rows={3} />
    </div>
  );
}
