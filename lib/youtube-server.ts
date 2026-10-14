import { createDecipheriv, createCipheriv, createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import { createClient } from "@supabase/supabase-js";
export const runtime = "nodejs";
function key() {
  const value = Buffer.from(process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY || "", "base64");
  if (value.length !== 32) throw new Error("YouTube token encryption is not configured.");
  return value;
}
function decrypt(value: string) {
  const p = JSON.parse(value);
  const d = createDecipheriv("aes-256-gcm", key(), Buffer.from(p.iv, "base64"));
  d.setAuthTag(Buffer.from(p.tag, "base64"));
  return JSON.parse(Buffer.concat([d.update(Buffer.from(p.data, "base64")), d.final()]).toString("utf8"));
}
function encrypt(payload: unknown) {
  const iv = randomBytes(12);
  const c = createCipheriv("aes-256-gcm", key(), iv);
  const data = Buffer.concat([c.update(JSON.stringify(payload), "utf8"), c.final()]);
  return JSON.stringify({ v: 1, alg: "A256GCM", iv: iv.toString("base64"), tag: c.getAuthTag().toString("base64"), data: data.toString("base64") });
}
function channelFromSession(request: Request): string | null {
  const cookieMatch = (request.headers.get("cookie") || "").match(/(?:^|;\s*)yt_dash_session=([^;]+)/);
  const authorization = request.headers.get("authorization") || "";
  const bearer = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  const rawSession = bearer || cookieMatch?.[1];
  if (!rawSession) return null;
  try {
    const parts = decodeURIComponent(rawSession).split(".");
    if (parts.length !== 3) return null;
    const [id, expiry, signature] = parts;
    if (!id || Number(expiry) < Math.floor(Date.now() / 1000)) return null;
    const expected = createHmac("sha256", key()).update(id + "." + expiry).digest();
    const actual = Buffer.from(signature, "base64url");
    return actual.length === expected.length && timingSafeEqual(actual, expected) ? id : null;
  } catch { return null; }
}
export async function authorizedYouTube(request: Request) {
  const channelId = channelFromSession(request);
  if (!channelId) throw new Error("Connect your YouTube channel first.");
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !serviceKey) throw new Error("Supabase server configuration is missing.");
  const supabase = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await supabase.from("youtube_connections").select("channel_id,encrypted_tokens").eq("channel_id", channelId).maybeSingle();
  if (error || !data?.encrypted_tokens) throw new Error("YouTube connection was not found. Please reconnect.");
  let tokens = decrypt(data.encrypted_tokens);
  if (Number(tokens.obtained_at || 0) + Number(tokens.expires_in || 3600) * 1000 - 60000 < Date.now()) {
    if (!tokens.refresh_token) throw new Error("Your YouTube session expired. Reconnect your channel.");
    const response = await fetch("https://oauth2.googleapis.com/token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ client_id: process.env.YOUTUBE_CLIENT_ID || "", client_secret: process.env.YOUTUBE_CLIENT_SECRET || "", refresh_token: tokens.refresh_token, grant_type: "refresh_token" }), cache: "no-store" });
    const refreshed = await response.json();
    if (!response.ok || !refreshed.access_token) throw new Error("Your YouTube session expired. Reconnect your channel.");
    tokens = { ...tokens, ...refreshed, refresh_token: tokens.refresh_token, obtained_at: Date.now() };
    const { error: saveError } = await supabase.from("youtube_connections").update({ encrypted_tokens: encrypt(tokens), updated_at: new Date().toISOString() }).eq("channel_id", channelId);
    if (saveError) throw new Error("Could not refresh the secure YouTube session.");
  }
  return { accessToken: tokens.access_token as string, channelId, google: async (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    headers.set("Authorization", "Bearer " + tokens.access_token);
    if (init.body && !headers.has("Content-Type") && typeof init.body === "string") headers.set("Content-Type", "application/json");
    const response = await fetch("https://www.googleapis.com/" + path, { ...init, headers, cache: "no-store" });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error?.message || "YouTube API request failed.");
    return body;
  }};
}
export function apiError(error: unknown, status = 400) {
  return Response.json({ error: error instanceof Error ? error.message : "Unexpected YouTube API error." }, { status, headers: { "Cache-Control": "no-store" } });
}
