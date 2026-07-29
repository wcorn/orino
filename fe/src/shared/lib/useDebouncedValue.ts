import { useEffect, useState } from "react";

/**
 * 값이 {@link delayMs} 동안 더 바뀌지 않을 때만 반영한다.
 * 타이핑마다 요청이 나가는 검색 입력 등에 쓴다.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
