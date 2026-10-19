import * as React from "react";

import { completeTimeDraft, parseTimeDraft } from "@/shared/lib/timeDraft";

import { Input } from "./input";

/** 서버가 `HH:mm:ss`로 줄 때가 있다. 화면은 분까지만 쓴다. */
function normalize(value: string): string {
  return /^\d{2}:\d{2}/.test(value) ? value.slice(0, 5) : "";
}

interface TimeInputProps extends Omit<
  React.ComponentProps<"input">,
  "type" | "value" | "defaultValue" | "onChange"
> {
  /** `HH:mm`(24시간) 또는 빈 문자열. 브라우저 `type="time"`과 같은 형식이다. */
  value: string;
  /** 완성된 `HH:mm`이나, 칸을 비웠을 때 빈 문자열만 올려 보낸다. */
  onChange: (value: string) => void;
}

/**
 * 24시간제 시각 입력(#1430).
 *
 * <p>브라우저 기본 `type="time"`을 쓰지 않는 이유: 한국어 로캘에서 <b>오전/오후를 따로
 * 고르게 하고</b>, 그 표기를 `lang` 등으로 바꿀 수 없다(브라우저·OS 로캘을 따른다).
 * 여기서는 숫자만 받아 `14:30`으로 맞춘다 — 폰에서는 숫자 키패드가 뜬다.
 *
 * <p>값의 계약은 `type="time"`과 같다(`HH:mm` 또는 빈 값). 그래서 바깥은 그대로 두고 칸만
 * 바꿔 끼운다. 다 치기 전(`14:3`)에는 아무것도 올려 보내지 않는다 — 덜 친 값으로 저장이
 * 돌면 안 된다.
 */
export function TimeInput({
  value,
  onChange,
  onBlur,
  onFocus,
  placeholder = "00:00",
  ...props
}: TimeInputProps) {
  const normalized = normalize(value);
  const [draft, setDraft] = React.useState(normalized);
  // 바깥 값이 바뀌면(불러오기·초기화) 칸을 맞춘다. 덜 친 동안에는 바깥 값이 그대로라
  // 손대지 않는다 — 그래서 치던 글자가 지워지지 않는다.
  const [synced, setSynced] = React.useState(normalized);
  if (normalized !== synced) {
    setSynced(normalized);
    setDraft(normalized);
  }

  const emit = (next: string) => {
    if (next !== normalized) {
      onChange(next);
    }
  };

  const invalid = draft.length === 5 && parseTimeDraft(draft).time === null;

  return (
    <Input
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder={placeholder}
      {...props}
      aria-invalid={invalid || props["aria-invalid"]}
      value={draft}
      onFocus={(event) => {
        // 탭으로 들어오면 전부 골라 치는 대로 새 값이 되게 한다.
        event.currentTarget.select();
        onFocus?.(event);
      }}
      onChange={(event) => {
        let raw = event.target.value;
        // 다 찬 칸(`09:00`) 뒤에 이어 치면 새로 치는 것으로 본다. 클릭으로 들어오면 커서가
        // 끝에 놓여 고르기가 풀리는데, 그때도 지우지 않고 바로 고쳐 칠 수 있어야 한다 —
        // 브라우저 시간 입력도 그렇게 덮어쓴다.
        if (draft.length === 5 && raw.length > 5 && raw.startsWith(draft)) {
          raw = raw.slice(5);
        }
        const { display, time } = parseTimeDraft(raw);
        setDraft(display);
        if (display === "") {
          emit("");
        } else if (time) {
          emit(time);
        }
      }}
      onBlur={(event) => {
        if (draft !== "") {
          const done = completeTimeDraft(draft);
          if (done) {
            setDraft(done);
            emit(done);
          } else {
            setDraft(normalized);
          }
        }
        onBlur?.(event);
      }}
    />
  );
}
