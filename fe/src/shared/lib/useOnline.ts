import { useEffect, useState } from "react";

/**
 * 온라인 여부. 지도 타일처럼 <b>캐시할 수 없는</b> 자원을 쓰는 화면이 미리 물어본다 —
 * 네트워크가 없으면 빈 지도만 뜨고 사용자는 앱이 멈춘 줄 안다.
 */
export function useOnline(): boolean {
  const [online, setOnline] = useState(() =>
    typeof navigator === "undefined" ? true : navigator.onLine,
  );

  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);

  return online;
}
