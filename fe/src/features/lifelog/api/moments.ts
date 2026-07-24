import { client } from "@/shared/api";

import type { FeedResponse, MomentCard, MomentWriteRequest } from "./types";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchFeed(params: {
  cursor?: string;
  size?: number;
  tag?: string;
}): Promise<FeedResponse> {
  const { data } = await client.get<ApiEnvelope<FeedResponse>>(
    "/lifelog/moments",
    { params },
  );
  return data.data;
}

export async function fetchMoment(id: number): Promise<MomentCard> {
  const { data } = await client.get<ApiEnvelope<MomentCard>>(
    `/lifelog/moments/${id}`,
  );
  return data.data;
}

export async function createMoment(
  request: MomentWriteRequest,
): Promise<MomentCard> {
  const { data } = await client.post<ApiEnvelope<MomentCard>>(
    "/lifelog/moments",
    request,
  );
  return data.data;
}

export async function updateMoment(
  id: number,
  request: MomentWriteRequest,
): Promise<MomentCard> {
  const { data } = await client.put<ApiEnvelope<MomentCard>>(
    `/lifelog/moments/${id}`,
    request,
  );
  return data.data;
}

export async function deleteMoment(id: number): Promise<void> {
  await client.delete(`/lifelog/moments/${id}`);
}

export async function fetchTagSuggestions(query: string): Promise<string[]> {
  const { data } = await client.get<ApiEnvelope<string[]>>("/lifelog/tags", {
    params: { q: query },
  });
  return data.data;
}
