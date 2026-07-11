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

// ProseMirror(coordsAtPos 등)가 텍스트 노드/Range의 getClientRects·getBoundingClientRect를
// 호출하는데 jsdom엔 없어 표 삽입 후 좌표 계산이 unhandled error를 낸다. 빈 값으로 폴리필.
const emptyRects = () =>
  Object.assign([] as unknown[], {
    item: () => null,
  }) as unknown as DOMRectList;
const zeroRect = () =>
  ({
    x: 0,
    y: 0,
    width: 0,
    height: 0,
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    toJSON: () => ({}),
  }) as DOMRect;
for (const proto of [globalThis.Text?.prototype, globalThis.Range?.prototype]) {
  if (proto && typeof proto.getClientRects !== "function") {
    proto.getClientRects = emptyRects;
  }
  if (proto && typeof proto.getBoundingClientRect !== "function") {
    proto.getBoundingClientRect = zeroRect;
  }
}

// jsdom에는 IntersectionObserver가 없다. 무한 스크롤 sentinel용 목.
// 생성된 인스턴스를 전역에 모아 테스트에서 교차(intersection)를 명시적으로 트리거한다.
// (src/test/io.ts의 triggerIntersection 헬퍼)
const ioInstances: MockIntersectionObserver[] = [];

class MockIntersectionObserver {
  private readonly callback: IntersectionObserverCallback;
  private readonly elements = new Set<Element>();

  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback;
    ioInstances.push(this);
  }

  observe(el: Element) {
    this.elements.add(el);
  }
  unobserve(el: Element) {
    this.elements.delete(el);
  }
  disconnect() {
    this.elements.clear();
  }

  /** 관찰 중인 모든 요소가 화면에 들어온 것으로 콜백을 호출한다. */
  trigger() {
    const entries = [...this.elements].map((target) => ({
      isIntersecting: true,
      target,
    })) as IntersectionObserverEntry[];
    if (entries.length > 0) {
      this.callback(entries, this as unknown as IntersectionObserver);
    }
  }
}

(
  globalThis as unknown as { IntersectionObserver: unknown }
).IntersectionObserver = MockIntersectionObserver;
(
  globalThis as unknown as { __ioInstances: MockIntersectionObserver[] }
).__ioInstances = ioInstances;

afterEach(() => {
  ioInstances.length = 0;
});

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  cleanup();
});
afterAll(() => server.close());
