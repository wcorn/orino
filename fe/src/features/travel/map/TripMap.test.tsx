import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { resetGoogleMapsLoader } from "./googleMaps";
import type { MapPoint } from "./TripMap";
import { TripMap } from "./TripMap";

/**
 * jsdom에는 구글 지도가 없다. <b>브라우저가 주는 것</b>이라 대역을 세운다 —
 * 우리 모듈을 바꿔치기하는 것이 아니라 경계 밖을 흉내 내는 것이다.
 */
interface MarkerSpy {
  position: unknown;
  content: HTMLElement;
  title: string;
  map: unknown;
  listeners: Record<string, () => void>;
}

const markers: MarkerSpy[] = [];
const polylines: { path: unknown[]; strokeColor: string }[] = [];
let fitted: { count: number; padding: unknown } | null = null;

function stubGoogleMaps() {
  markers.length = 0;
  polylines.length = 0;
  fitted = null;

  class Bounds {
    points: unknown[] = [];
    extend(p: unknown) {
      this.points.push(p);
    }
  }

  (window as { google?: unknown }).google = {
    maps: {
      Map: class {
        setCenter() {}
        setZoom() {}
        fitBounds(b: Bounds, padding: unknown) {
          fitted = { count: b.points.length, padding };
        }
      },
      LatLngBounds: Bounds,
      Polyline: class {
        constructor(opts: { path: unknown[]; strokeColor: string }) {
          polylines.push(opts);
        }
        setMap() {}
      },
      marker: {
        AdvancedMarkerElement: class {
          position: unknown;
          content: HTMLElement;
          title: string;
          map: unknown;
          listeners: Record<string, () => void> = {};
          constructor(opts: {
            position: unknown;
            content: HTMLElement;
            title: string;
            map: unknown;
          }) {
            this.position = opts.position;
            this.content = opts.content;
            this.title = opts.title;
            this.map = opts.map;
            markers.push(this as unknown as MarkerSpy);
          }
          addListener(event: string, fn: () => void) {
            this.listeners[event] = fn;
          }
        },
      },
    },
  };
}

function point(key: number, title: string, order: number): MapPoint {
  return {
    key,
    order,
    title,
    lat: 35.7 + key / 100,
    lng: 139.7 + key / 100,
  };
}

describe("TripMap", () => {
  beforeEach(() => {
    resetGoogleMapsLoader();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    resetGoogleMapsLoader();
    delete (window as { google?: unknown }).google;
  });

  describe("지도를 쓸 수 없을 때", () => {
    it("키가 없으면 안내를 대신 보여준다 — 빈 회색 상자로 두지 않는다", async () => {
      delete (window as { google?: unknown }).google;
      render(
        <TripMap
          points={[point(1, "센소지", 1)]}
          selectedKey={null}
          onSelect={() => {}}
          label="하루 동선 지도"
        />,
      );

      expect(
        await screen.findByText("지도를 불러오지 못했어요."),
      ).toBeInTheDocument();
    });
  });

  describe("지도를 쓸 수 있을 때", () => {
    beforeEach(() => {
      stubGoogleMaps();
    });

    it("점마다 번호 핀을 찍는다", async () => {
      render(
        <TripMap
          points={[point(1, "센소지", 1), point(2, "시부야", 2)]}
          selectedKey={null}
          onSelect={() => {}}
          label="하루 동선 지도"
        />,
      );

      await waitFor(() => expect(markers).toHaveLength(2));
      expect(markers.map((m) => m.content.textContent)).toEqual(["1", "2"]);
      expect(markers[0].title).toBe("1. 센소지");
    });

    it("핀을 누르면 그 점을 고른다", async () => {
      const onSelect = vi.fn();
      render(
        <TripMap
          points={[point(7, "센소지", 1)]}
          selectedKey={null}
          onSelect={onSelect}
          label="하루 동선 지도"
        />,
      );

      await waitFor(() => expect(markers).toHaveLength(1));
      markers[0].listeners.click();

      expect(onSelect).toHaveBeenCalledWith(7);
    });

    it("두 곳 이상이면 순서대로 직선을 잇는다 — 실제 경로가 아니라 순서다", async () => {
      render(
        <TripMap
          points={[point(1, "a", 1), point(2, "b", 2), point(3, "c", 3)]}
          selectedKey={null}
          onSelect={() => {}}
          label="하루 동선 지도"
        />,
      );

      await waitFor(() => expect(polylines).toHaveLength(1));
      expect(polylines[0].path).toHaveLength(3);
    });

    it("경로를 따로 주면 점이 아니라 그 경로로 잇는다 — 오간 자취가 선에 남는다", async () => {
      // 도쿄 → 닛코 → 도쿄: 점은 둘, 획은 둘이다.
      render(
        <TripMap
          points={[point(1, "도쿄", 1), point(2, "닛코", 2)]}
          path={[
            { lat: 35.68, lng: 139.76 },
            { lat: 36.75, lng: 139.6 },
            { lat: 35.68, lng: 139.76 },
          ]}
          selectedKey={null}
          onSelect={() => {}}
          label="여행 전체 지도"
        />,
      );

      await waitFor(() => expect(polylines).toHaveLength(1));
      expect(polylines[0].path).toHaveLength(3);
      expect(markers).toHaveLength(2);
    });

    it("한 곳뿐이면 선을 긋지 않는다", async () => {
      render(
        <TripMap
          points={[point(1, "a", 1)]}
          selectedKey={null}
          onSelect={() => {}}
          label="하루 동선 지도"
        />,
      );

      await waitFor(() => expect(markers).toHaveLength(1));
      expect(polylines).toHaveLength(0);
    });

    it("핀이 전부 들어오게 범위를 맞춘다", async () => {
      render(
        <TripMap
          points={[point(1, "a", 1), point(2, "b", 2)]}
          selectedKey={null}
          onSelect={() => {}}
          label="하루 동선 지도"
        />,
      );

      await waitFor(() => expect(fitted).not.toBeNull());
      expect(fitted).toEqual({ count: 2, padding: 24 });
    });

    it("고른 핀은 크게 그린다 — 어느 것을 보고 있는지 알아야 한다", async () => {
      render(
        <TripMap
          points={[point(1, "a", 1), point(2, "b", 2)]}
          selectedKey={2}
          onSelect={() => {}}
          label="하루 동선 지도"
        />,
      );

      await waitFor(() => expect(markers).toHaveLength(2));
      expect(markers[0].content.style.width).toBe("26px");
      expect(markers[1].content.style.width).toBe("32px");
    });
  });
});
