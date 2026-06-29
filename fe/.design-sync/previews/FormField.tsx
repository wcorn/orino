import { FormField, Input } from "orino-fe";

export function Default() {
  return (
    <div style={{ width: 320 }}>
      <FormField label="제목" htmlFor="ff-title">
        <Input id="ff-title" defaultValue="자료구조 정리" />
      </FormField>
    </div>
  );
}

export function WithError() {
  return (
    <div style={{ width: 320 }}>
      <FormField label="라벨" htmlFor="ff-label" error="라벨을 입력해 주세요.">
        <Input id="ff-label" placeholder="예: 개인 프로젝트" />
      </FormField>
    </div>
  );
}
