import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { MomentCard } from "../api/types";
import { FlowMap } from "./FlowMap";

function moment(
  id: number,
  lat: number | null,
  lng: number | null,
): MomentCard {
  return {
    id,
    occurredAt: "2026-07-20T00:00:00Z",
    body: null,
    mood: null,
    lat,
    lng,
    placeName: null,
    tags: [],
    photos: [],
    flows: [],
    createdAt: "2026-07-20T00:00:00Z",
  };
}

describe("FlowMap", () => {
  it("좌표 있는 기록이 없으면 안내를 보여준다(지도 미표시)", () => {
    render(<FlowMap moments={[moment(1, null, null)]} />);
    expect(
      screen.getByText(/위치 정보가 있는 기록이 없어요/),
    ).toBeInTheDocument();
  });
});
