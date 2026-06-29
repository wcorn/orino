import { FormField, Input } from "orino-fe";

export function Default() {
  return <Input placeholder="이름을 입력하세요" defaultValue="홍길동" />;
}

export function Disabled() {
  return <Input placeholder="비활성 입력" disabled />;
}

export function WithLabel() {
  return (
    <FormField label="이메일" htmlFor="email-preview">
      <Input id="email-preview" type="email" placeholder="you@example.com" />
    </FormField>
  );
}
