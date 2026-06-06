import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";

import { server } from "./mocks/server";

// jsdom에는 URL.createObjectURL/revokeObjectURL이 없어 이미지 즉시 미리보기용 polyfill.
if (!URL.createObjectURL) {
  URL.createObjectURL = () => "blob:mock";
}
if (!URL.revokeObjectURL) {
  URL.revokeObjectURL = () => {};
}

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  cleanup();
});
afterAll(() => server.close());
