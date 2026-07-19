import request from '@/utils/request';

export interface WebsiteLinkCode {
  /** Six-to-eight-character, one-time code entered on the CareerLoop website. */
  code: string;
  /** Remaining validity reported by the server. The current policy is 10 minutes. */
  expiresInSeconds: number;
}

interface LinkCodePayload {
  code?: string | number;
  linkCode?: string | number;
  expiresInSeconds?: number;
}

/**
 * Creates a short-lived code that lets the signed-in student explicitly link
 * this mini-program profile to the CareerLoop website. The bridge credential
 * never reaches the client.
 */
export const createWebsiteLinkCodeApi = async (): Promise<WebsiteLinkCode> => {
  const payload = await request<LinkCodePayload>({
    url: '/api/integration/link-code',
    method: 'POST',
  });
  const rawCode = payload?.code ?? payload?.linkCode;
  if (rawCode === undefined || rawCode === null || String(rawCode).trim() === '') {
    throw new Error('连接码生成失败，请稍后重试');
  }
  return {
    code: String(rawCode).trim().toUpperCase(),
    expiresInSeconds: Number(payload?.expiresInSeconds) > 0
      ? Number(payload.expiresInSeconds)
      : 600,
  };
};
