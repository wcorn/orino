import { LoadingText } from "orino-fe";

export function Default() {
  return <LoadingText />;
}

export function CustomMessage() {
  return <LoadingText>복습 카드를 불러오는 중…</LoadingText>;
}
