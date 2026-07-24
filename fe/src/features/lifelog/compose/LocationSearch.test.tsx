import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { LocationSearch } from "./LocationSearch";

const API = "https://api.orino.dev/api";

describe("LocationSearch", () => {
  it("검색 결과를 보여주고 선택하면 좌표·장소명을 올린다", async () => {
    server.use(
      http.get(`${API}/lifelog/geocode/search`, ({ request }) => {
        const q = new URL(request.url).searchParams.get("q");
        expect(q).toBe("성산");
        return HttpResponse.json({
          code: "OK",
          data: [
            { placeName: "성산일출봉", lat: 33.458, lng: 126.942 },
            { placeName: "성산읍", lat: 33.44, lng: 126.9 },
          ],
        });
      }),
    );
    const onSelect = vi.fn();
    renderWithRouter(<LocationSearch onSelect={onSelect} />);

    await userEvent.type(screen.getByLabelText("장소 검색"), "성산");

    const option = await screen.findByRole("button", { name: "성산일출봉" });
    await userEvent.click(option);

    expect(onSelect).toHaveBeenCalledWith({
      placeName: "성산일출봉",
      lat: 33.458,
      lng: 126.942,
    });
  });

  it("2자 미만이면 검색하지 않는다", async () => {
    let called = false;
    server.use(
      http.get(`${API}/lifelog/geocode/search`, () => {
        called = true;
        return HttpResponse.json({ code: "OK", data: [] });
      }),
    );
    renderWithRouter(<LocationSearch onSelect={vi.fn()} />);

    await userEvent.type(screen.getByLabelText("장소 검색"), "성");
    // 결과 목록 자체가 뜨지 않는다(2자 미만).
    expect(screen.queryByText("결과 없음")).not.toBeInTheDocument();
    expect(called).toBe(false);
  });
});
