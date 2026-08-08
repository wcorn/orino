import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 서버가 내려주는 VAPID 공개키. 키가 없으면 null이고 화면은 알림 UI를 감춘다. */
export async function fetchPushPublicKey(): Promise<string | null> {
  const { data } = await client.get<ApiEnvelope<{ publicKey: string | null }>>(
    "/travel/push/public-key",
  );
  return data.data.publicKey;
}

/** 브라우저의 `PushSubscription.toJSON()`을 그대로 보낸다 — 손으로 풀지 않는다. */
export async function subscribePush(
  subscription: PushSubscriptionJSON,
  userAgent: string,
): Promise<void> {
  await client.post("/travel/push/subscriptions", {
    endpoint: subscription.endpoint,
    keys: subscription.keys,
    userAgent,
  });
}

export async function unsubscribePush(endpoint: string): Promise<void> {
  await client.delete("/travel/push/subscriptions", { data: { endpoint } });
}

/** 즉시 발송. 실기기 검증의 첫 단추다. 전달된 구독 수를 돌려준다. */
export async function sendTestPush(): Promise<number> {
  const { data } = await client.post<ApiEnvelope<number>>(
    "/travel/push/test",
    {},
  );
  return data.data;
}
