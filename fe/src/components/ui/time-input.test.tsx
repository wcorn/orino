import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { TimeInput } from "./time-input";

/** 바깥 상태까지 붙인 실제 사용 모양. 올려 보낸 값을 기록한다. */
function Harness({
  initial = "",
  onSent,
}: {
  initial?: string;
  onSent?: (value: string) => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <TimeInput
        aria-label="시각"
        value={value}
        onChange={(next) => {
          onSent?.(next);
          setValue(next);
        }}
      />
      <button type="button" onClick={() => setValue("07:15")}>
        불러오기
      </button>
      <output aria-label="저장값">{value}</output>
    </>
  );
}

describe("TimeInput", () => {
  it("오전/오후 없이 24시간제로 받는다 — 숫자만 치면 콜론이 붙는다", async () => {
    render(<Harness />);
    const input = screen.getByLabelText("시각");

    await userEvent.type(input, "2315");

    expect(input).toHaveValue("23:15");
    expect(screen.getByLabelText("저장값")).toHaveTextContent("23:15");
    // 폰에서는 숫자 키패드가 뜬다. 브라우저 시간 선택기(오전/오후)는 쓰지 않는다.
    expect(input).toHaveAttribute("type", "text");
    expect(input).toHaveAttribute("inputmode", "numeric");
  });

  it("다 치기 전에는 올려 보내지 않는다 — 덜 친 값으로 저장이 돌면 안 된다", async () => {
    const onSent = vi.fn();
    render(<Harness initial="09:00" onSent={onSent} />);
    const input = screen.getByLabelText("시각");

    await userEvent.clear(input);
    await userEvent.type(input, "143");

    expect(input).toHaveValue("14:3");
    // 비운 순간의 빈 값만 올라갔다.
    expect(onSent.mock.calls).toEqual([[""]]);

    await userEvent.type(input, "0");
    expect(onSent).toHaveBeenLastCalledWith("14:30");
  });

  it("값이 찬 칸에 들어가 바로 치면 덮어쓴다 — 지우고 칠 필요가 없다", async () => {
    render(<Harness initial="00:00" />);
    const input = screen.getByLabelText("시각");

    // 클릭으로 들어온다. 다섯 글자가 꽉 찬 칸이라 고르지 않으면 한 글자도 안 들어간다.
    await userEvent.click(input);
    await userEvent.keyboard("2145");

    expect(input).toHaveValue("21:45");
    expect(screen.getByLabelText("저장값")).toHaveTextContent("21:45");
  });

  it("시만 치고 칸을 떠나면 정각으로 마무리한다", async () => {
    render(<Harness />);
    const input = screen.getByLabelText("시각");

    await userEvent.type(input, "9");
    await userEvent.tab();

    expect(input).toHaveValue("09:00");
    expect(screen.getByLabelText("저장값")).toHaveTextContent("09:00");
  });

  it("없는 시각은 표시만 하고, 칸을 떠나면 원래 값으로 돌아간다", async () => {
    const onSent = vi.fn();
    render(<Harness initial="10:00" onSent={onSent} />);
    const input = screen.getByLabelText("시각");

    await userEvent.clear(input);
    onSent.mockClear();
    await userEvent.type(input, "2500");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(onSent).not.toHaveBeenCalled();

    await userEvent.tab();
    // 비웠을 때 빈 값이 올라갔으므로 원래 값은 빈 값이다.
    expect(input).toHaveValue("");
  });

  it("바깥에서 값이 바뀌면 칸도 따라간다", async () => {
    render(<Harness initial="09:00" />);

    await userEvent.click(screen.getByRole("button", { name: "불러오기" }));

    expect(screen.getByLabelText("시각")).toHaveValue("07:15");
  });

  it("서버가 초까지 줘도 분까지만 보인다", () => {
    render(
      <TimeInput aria-label="시각" value="15:00:00" onChange={() => {}} />,
    );
    expect(screen.getByLabelText("시각")).toHaveValue("15:00");
  });
});
