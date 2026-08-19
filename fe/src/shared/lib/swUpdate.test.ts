import { describe, expect, it, vi } from "vitest";

import { createUpdateWatcher, type UpdateWatcherOptions } from "./swUpdate";

/** state를 바꾸면 statechange를 쏘는 최소한의 가짜 SW. */
class FakeWorker extends EventTarget {
  state: ServiceWorkerState = "installing";

  moveTo(state: ServiceWorkerState) {
    this.state = state;
    this.dispatchEvent(new Event("statechange"));
  }
}

class FakeRegistration extends EventTarget {
  installing: FakeWorker | null = null;
  waiting: FakeWorker | null = null;

  /** 새 SW의 설치가 시작된 상황. */
  startInstalling(worker: FakeWorker) {
    this.installing = worker;
    this.dispatchEvent(new Event("updatefound"));
  }
}

function setup(overrides: Partial<UpdateWatcherOptions> = {}) {
  const applyUpdate = vi.fn();
  const onApplying = vi.fn();
  const reload = vi.fn();
  // 인자로 받은 apply를 붙잡아 둔다 — "새로고침"을 누른 상황을 테스트에서 재현한다.
  let apply: (() => void) | null = null;
  const onUpdateFound = vi.fn((fn: () => void) => {
    apply = fn;
  });

  const watcher = createUpdateWatcher({
    isControlled: () => true,
    applyUpdate,
    onUpdateFound,
    onApplying,
    reload,
    ...overrides,
  });

  const registration = new FakeRegistration();

  return {
    watcher,
    registration,
    applyUpdate,
    onApplying,
    onUpdateFound,
    reload,
    /** 안내가 뜬 뒤 사용자가 "새로고침"을 누른다. */
    clickRefresh: () => {
      if (!apply) throw new Error("아직 안내가 뜨지 않았다");
      apply();
    },
    watch: () =>
      watcher.watch(registration as unknown as ServiceWorkerRegistration),
  };
}

describe("createUpdateWatcher", () => {
  it("설치가 끝나기 전에 알린다 — 이게 이 감시의 존재 이유다", () => {
    const { watch, registration, onUpdateFound } = setup();
    watch();

    const worker = new FakeWorker();
    registration.startInstalling(worker);

    expect(onUpdateFound).toHaveBeenCalledTimes(1);
    // 아직 내려받는 중인데도 알렸다.
    expect(worker.state).toBe("installing");
  });

  it("첫 설치는 알리지 않는다 — 교체가 아니라 처음 얹는 것이다", () => {
    const { watch, registration, onUpdateFound } = setup({
      isControlled: () => false,
    });
    watch();

    registration.startInstalling(new FakeWorker());

    expect(onUpdateFound).not.toHaveBeenCalled();
  });

  it("감시를 붙이기 전에 설치가 시작됐어도 알린다 — updatefound를 놓친 방문", () => {
    // 제어 중인 페이지로 이동하면 브라우저가 앱보다 먼저 sw.js를 확인한다.
    // 그 방문에서는 updatefound가 이미 지나가 있어서, 이벤트만 기다리면 영영 알리지 못한다.
    const { watch, registration, onUpdateFound, clickRefresh, applyUpdate } =
      setup();
    const worker = new FakeWorker();
    registration.installing = worker;

    watch();

    expect(onUpdateFound).toHaveBeenCalledTimes(1);
    // 놓쳤어도 적용 흐름은 같다 — 설치가 끝난 뒤에 적용한다.
    clickRefresh();
    expect(applyUpdate).not.toHaveBeenCalled();
    worker.moveTo("installed");
    expect(applyUpdate).toHaveBeenCalledTimes(1);
  });

  it("설치가 시작돼 있어도 첫 설치면 알리지 않는다", () => {
    const { watch, registration, onUpdateFound } = setup({
      isControlled: () => false,
    });
    registration.installing = new FakeWorker();

    watch();

    expect(onUpdateFound).not.toHaveBeenCalled();
  });

  it("이미 대기 중인 새 SW가 있으면 감시를 붙이는 즉시 알린다", () => {
    const { watch, registration, onUpdateFound } = setup();
    registration.waiting = new FakeWorker();
    registration.waiting.state = "installed";

    watch();

    expect(onUpdateFound).toHaveBeenCalledTimes(1);
  });

  it("내려받는 중에 누르면 준비 중임을 알리고, 끝난 뒤에 적용한다", () => {
    const { watch, registration, clickRefresh, applyUpdate, onApplying } =
      setup();
    watch();
    const worker = new FakeWorker();
    registration.startInstalling(worker);

    clickRefresh();

    // 지금 보내면 workbox가 조용히 무시한다(waiting이 없다). 그래서 기다린다.
    expect(onApplying).toHaveBeenCalledTimes(1);
    expect(applyUpdate).not.toHaveBeenCalled();

    worker.moveTo("installed");

    expect(applyUpdate).toHaveBeenCalledTimes(1);
  });

  it("이미 대기 중이면 곧바로 적용한다 — 기다린다는 안내를 띄우지 않는다", () => {
    const { watch, registration, clickRefresh, applyUpdate, onApplying } =
      setup();
    registration.waiting = new FakeWorker();
    registration.waiting.state = "installed";
    watch();

    clickRefresh();

    expect(applyUpdate).toHaveBeenCalledTimes(1);
    expect(onApplying).not.toHaveBeenCalled();
  });

  it("기다리던 설치가 깨지면 새로고침으로 떨어진다", () => {
    const { watch, registration, clickRefresh, applyUpdate, reload } = setup();
    watch();
    const worker = new FakeWorker();
    registration.startInstalling(worker);
    clickRefresh();

    worker.moveTo("redundant");

    expect(applyUpdate).not.toHaveBeenCalled();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("같은 등록을 두 번 감시해도 한 번만 알린다", () => {
    const { watch, registration, onUpdateFound } = setup();
    watch();
    watch();

    registration.startInstalling(new FakeWorker());

    expect(onUpdateFound).toHaveBeenCalledTimes(1);
  });

  it("뒷받침 경로(onNeedRefresh)가 이어 붙어도 안내는 하나다", () => {
    const { watch, watcher, registration, onUpdateFound } = setup();
    watch();
    registration.startInstalling(new FakeWorker());

    // 설치가 끝나면 플러그인이 onNeedRefresh를 부른다 — 이미 알린 뒤다.
    watcher.notifyReady();

    expect(onUpdateFound).toHaveBeenCalledTimes(1);
  });

  it("감시가 updatefound를 놓쳤으면 뒷받침 경로가 알리고, 곧바로 적용된다", () => {
    const { watcher, clickRefresh, onUpdateFound, applyUpdate } = setup();

    watcher.notifyReady();

    expect(onUpdateFound).toHaveBeenCalledTimes(1);
    clickRefresh();
    expect(applyUpdate).toHaveBeenCalledTimes(1);
  });
});
