import { useCallback, useEffect, useState } from "react";

import {
  fetchPushPublicKey,
  subscribePush,
  unsubscribePush,
} from "@/features/travel/api/push";
import {
  currentSubscription,
  isPushSupported,
  registration,
  toApplicationServerKey,
} from "@/features/travel/push/pushSubscription";

export type PushState =
  /** 이 브라우저가 웹푸시를 못 한다. */
  | "unsupported"
  /** 서버에 VAPID 키가 없다 — 기능이 아직 안 켜진 것이다. */
  | "unavailable"
  | "loading"
  /** 권한은 있는데 아직 구독하지 않았다. */
  | "idle"
  | "subscribed"
  /** 사용자가 거부했다. 브라우저 설정에서 풀어야 한다. */
  | "denied";

/**
 * 알림 구독.
 *
 * <p>상태를 서버가 아니라 <b>브라우저</b>에서 읽는다 — 서버에 구독 기록이 남아 있어도
 * 사용자가 브라우저 설정에서 권한을 끊었으면 실제로는 안 온다. 화면이 "구독됨"이라고
 * 말하는데 알림이 안 오는 상태가 제일 나쁘다.
 */
export function usePushSubscription() {
  const [state, setState] = useState<PushState>("loading");
  const [pending, setPending] = useState(false);

  const refresh = useCallback(async () => {
    if (!isPushSupported()) {
      setState("unsupported");
      return;
    }
    if (Notification.permission === "denied") {
      setState("denied");
      return;
    }
    // SW가 없으면 구독 자체가 성립하지 않는다. dev 서버가 그렇고, 운영에서도
    // 등록이 실패하면 마찬가지다 — 기다리지 말고 그대로 말한다.
    if (!(await registration())) {
      setState("unsupported");
      return;
    }
    const publicKey = await fetchPushPublicKey().catch(() => null);
    if (!publicKey) {
      setState("unavailable");
      return;
    }
    setState((await currentSubscription()) ? "subscribed" : "idle");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  /** 권한 요청 → 구독 → 서버 등록. 한 번에 한다 — 사용자가 누르는 버튼은 하나다. */
  const subscribe = useCallback(async () => {
    setPending(true);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setState(permission === "denied" ? "denied" : "idle");
        return false;
      }
      const publicKey = await fetchPushPublicKey();
      if (!publicKey) {
        setState("unavailable");
        return false;
      }
      const active = await registration();
      if (!active) {
        setState("unsupported");
        return false;
      }
      const subscription = await active.pushManager.subscribe({
        // 서버가 이 키로 서명하고, 푸시 서비스가 그 서명을 검증한다.
        userVisibleOnly: true,
        applicationServerKey: toApplicationServerKey(publicKey),
      });
      await subscribePush(subscription.toJSON(), navigator.userAgent);
      setState("subscribed");
      return true;
    } finally {
      setPending(false);
    }
  }, []);

  /** 해지는 브라우저와 서버 양쪽에서 한다 — 한쪽만 지우면 유령 구독이 남는다. */
  const unsubscribe = useCallback(async () => {
    setPending(true);
    try {
      const subscription = await currentSubscription();
      if (subscription) {
        await unsubscribePush(subscription.endpoint);
        await subscription.unsubscribe();
      }
      setState("idle");
    } finally {
      setPending(false);
    }
  }, []);

  return { state, pending, subscribe, unsubscribe, refresh };
}
