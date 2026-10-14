import { NextResponse } from "next/server";
import { createCipheriv, createHmac, randomBytes } from "node:crypto";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";

function encryptTokens(payload: unknown) {
  const raw = process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY;
  if (!raw) throw new Error("token_encryption_not_configured");
  const key = Buffer.from(raw, "base64");
  if (key.length !== 32) throw new Error("token_encryption_key_must_be_32_bytes_base64");
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(payload), "utf8"), cipher.final()]);
  return JSON.stringify({ v: 1, alg: "A256GCM", iv: iv.toString("base64"), tag: cipher.getAuthTag().toString("base64"), data: encrypted.toString("base64") });
}

export async function GET(request: Request) {
  const url = new URL(request.url);
  const origin = process.env.APP_URL || url.origin;
  const cookieHeader = request.headers.get("cookie") ?? "";
  const nativeApp = cookieHeader.match(/(?:^|;\s*)yt_oauth_native=([^;]*)/)?.[1] === "1";
  const fail = (reason: string) => nativeApp
    ? NextResponse.redirect(new URL("ytdash://oauth/callback?error=" + encodeURIComponent(reason)))
    : NextResponse.redirect(new URL("/?error=" + encodeURIComponent(reason), origin));
  const code = url.searchParams.get("code");
  const state = url.searchParams.get("state");
  const cookieState = cookieHeader.match(/(?:^|;\s*)yt_oauth_state=([^;]*)/)?.[1];
  const verifier = cookieHeader.match(/(?:^|;\s*)yt_oauth_verifier=([^;]*)/)?.[1];
  if (url.searchParams.has("error")) return fail("google_authorization_denied");
  if (!code || !state || !cookieState || decodeURIComponent(cookieState) !== state || !verifier) return fail("oauth_state_invalid");
  const clientId = process.env.YOUTUBE_CLIENT_ID;
  const clientSecret = process.env.YOUTUBE_CLIENT_SECRET;
  const redirectUri = process.env.YOUTUBE_REDIRECT_URI;
  if (!clientId || !clientSecret || !redirectUri) return fail("oauth_not_configured");

  try {
    const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ code, client_id: clientId, client_secret: clientSecret, redirect_uri: redirectUri, grant_type: "authorization_code", code_verifier: decodeURIComponent(verifier) }),
      cache: "no-store"
    });
    const tokens = await tokenResponse.json();
    if (!tokenResponse.ok || !tokens.access_token) return fail("token_exchange_failed");

    const channelResponse = await fetch("https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true", {
      headers: { Authorization: "Bearer " + tokens.access_token }, cache: "no-store"
    });
    const channelData = await channelResponse.json();
    const channel = channelData.items?.[0];
    if (!channel?.id) return fail("no_youtube_channel_found");

    const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
    if (!supabaseUrl || !serviceKey) return fail("supabase_not_configured");
    const supabase = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const tokenPayload = { ...tokens, obtained_at: Date.now(), channel: { id: channel.id, title: channel.snippet?.title ?? "YouTube channel", thumbnail: channel.snippet?.thumbnails?.default?.url ?? null, subscribers: channel.statistics?.subscriberCount ?? null, views: channel.statistics?.viewCount ?? null, videos: channel.statistics?.videoCount ?? null } };
    const { error } = await supabase.from("youtube_connections").upsert({ channel_id: channel.id, encrypted_tokens: encryptTokens(tokenPayload), updated_at: new Date().toISOString() }, { onConflict: "channel_id" });
    if (error) return fail("connection_storage_failed");
    const response = nativeApp
      ? NextResponse.redirect(new URL("ytdash://oauth/callback?connected=1"))
      : NextResponse.redirect(new URL("/?connected=1", origin));
    response.cookies.delete("yt_oauth_state");
    response.cookies.delete("yt_oauth_verifier");
    response.cookies.delete("yt_oauth_native");
    const sessionExpiry = Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30;
    const sessionPayload = channel.id + "." + sessionExpiry;
    const sessionKey = Buffer.from(process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY || "", "base64");
    const sessionSignature = createHmac("sha256", sessionKey).update(sessionPayload).digest("base64url");
    response.cookies.set("yt_dash_session", sessionPayload + "." + sessionSignature, {
      httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax", path: "/", maxAge: 60 * 60 * 24 * 30
    });
    return response;
  } catch {
    return fail("connection_failed");
  }
}