import { NextResponse } from "next/server";
import { createCipheriv, createDecipheriv, createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function encryptionKey() {
  const key = Buffer.from(process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY || "", "base64");
  if (key.length !== 32) throw new Error("invalid_encryption_config");
  return key;
}
function decryptTokens(value: string) {
  const parsed = JSON.parse(value);
  if (parsed.alg !== "A256GCM") throw new Error("invalid_encryption_format");
  const decipher = createDecipheriv("aes-256-gcm", encryptionKey(), Buffer.from(parsed.iv, "base64"));
  decipher.setAuthTag(Buffer.from(parsed.tag, "base64"));
  return JSON.parse(Buffer.concat([decipher.update(Buffer.from(parsed.data, "base64")), decipher.final()]).toString("utf8"));
}
function encryptTokens(payload: unknown) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", encryptionKey(), iv);
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(payload), "utf8"), cipher.final()]);
  return JSON.stringify({ v: 1, alg: "A256GCM", iv: iv.toString("base64"), tag: cipher.getAuthTag().toString("base64"), data: encrypted.toString("base64") });
}
async function googleFetch(path: string, token: string) {
  const target = path.startsWith("https://") ? path : "https://www.googleapis.com/" + path;
  const response = await fetch(target, { headers: { Authorization: "Bearer " + token }, cache: "no-store" });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error?.message || "youtube_api_error");
  return data;
}

function sessionChannel(request: Request): string | null {
  const cookie = request.headers.get("cookie") || "";
  const value = (request.headers.get("authorization") || "").match(/^Bearer\s+(.+)$/i)?.[1]
    || cookie.match(/(?:^|;\s*)yt_dash_session=([^;]+)/)?.[1];
  if (!value) return null;
  try {
    const decoded = decodeURIComponent(value);
    const parts = decoded.split(".");
    if (parts.length !== 3) return null;
    const [channelId, expiry, signature] = parts;
    if (!channelId || Number(expiry) < Math.floor(Date.now() / 1000)) return null;
    const key = encryptionKey();
    const expected = createHmac("sha256", key).update(channelId + "." + expiry).digest();
    const actual = Buffer.from(signature, "base64url");
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) return null;
    return channelId;
  } catch { return null; }
}

export async function GET(request: Request) {
  const empty = { connected: false };
  const channelId = sessionChannel(request);
  if (!channelId) return NextResponse.json(empty, { headers: { "Cache-Control": "no-store" } });
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!supabaseUrl || !serviceKey || !process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY) return NextResponse.json(empty, { headers: { "Cache-Control": "no-store" } });
  try {
    const supabase = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data, error } = await supabase.from("youtube_connections").select("channel_id,encrypted_tokens,updated_at").eq("channel_id", channelId).maybeSingle();
    if (error || !data?.encrypted_tokens) return NextResponse.json(empty);
    let tokens = decryptTokens(data.encrypted_tokens);
    const expiry = Number(tokens.obtained_at || 0) + Number(tokens.expires_in || 3600) * 1000 - 60000;
    if (expiry < Date.now()) {
      if (!tokens.refresh_token) return NextResponse.json({ connected: true, channel: tokens.channel, error: "reconnect_required" });
      const refreshResponse = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id: process.env.YOUTUBE_CLIENT_ID || "",
          client_secret: process.env.YOUTUBE_CLIENT_SECRET || "",
          refresh_token: tokens.refresh_token,
          grant_type: "refresh_token"
        }),
        cache: "no-store"
      });
      const refreshed = await refreshResponse.json();
      if (!refreshResponse.ok || !refreshed.access_token) return NextResponse.json({ connected: true, channel: tokens.channel, error: "reconnect_required" });
      tokens = { ...tokens, ...refreshed, refresh_token: tokens.refresh_token, obtained_at: Date.now(), channel: tokens.channel };
      const { error: saveError } = await supabase.from("youtube_connections").update({
        encrypted_tokens: encryptTokens(tokens),
        updated_at: new Date().toISOString()
      }).eq("channel_id", data.channel_id);
      if (saveError) return NextResponse.json({ connected: true, channel: tokens.channel, error: "token_persistence_failed" });
    }

    const channel = tokens.channel;
    const now = new Date();
    const requestedDays = Number(new URL(request.url).searchParams.get("days") || 28);
    const days = [7, 28, 90, 365].includes(requestedDays) ? requestedDays : 28;
    // Analytics reports are more reliable through yesterday; today's totals can be incomplete.
    const reportEnd = new Date(now.getTime() - 86400000);
    const end = reportEnd.toISOString().slice(0, 10);
    const startDate = new Date(reportEnd.getTime() - (days - 1) * 86400000).toISOString().slice(0, 10);
    let analytics: Record<string, unknown> = {};
    try {
      const query = new URLSearchParams({
        ids: "channel==MINE",
        startDate,
        endDate: end,
        metrics: "views,estimatedMinutesWatched,subscribersGained,likes,comments,shares,averageViewDuration",
        dimensions: "day",
        sort: "day"
      });
      const report = await googleFetch("https://youtubeanalytics.googleapis.com/v2/reports?" + query.toString(), tokens.access_token);
      const rows: unknown[][] = report.rows || [];
      analytics = {
        views: rows.reduce((sum, row) => sum + Number(row[1] || 0), 0).toLocaleString("en-US"),
        watchTime: Math.round(rows.reduce((sum, row) => sum + Number(row[2] || 0), 0) / 60).toLocaleString("en-US"),
        subscribers: "+" + rows.reduce((sum, row) => sum + Number(row[3] || 0), 0).toLocaleString("en-US"),
        likes: rows.reduce((sum, row) => sum + Number(row[4] || 0), 0).toLocaleString("en-US"),
        comments: rows.reduce((sum, row) => sum + Number(row[5] || 0), 0).toLocaleString("en-US"),
        shares: rows.reduce((sum, row) => sum + Number(row[6] || 0), 0).toLocaleString("en-US"),
        averageViewDuration: rows.length ? Math.round(rows.reduce((sum, row) => sum + Number(row[7] || 0), 0) / rows.length) : 0,
        days,
        startDate,
        endDate: end,
        daily: rows.map(row => ({ date: String(row[0] || ""), views: Number(row[1] || 0), watchMinutes: Number(row[2] || 0), subscribersGained: Number(row[3] || 0), likes: Number(row[4] || 0), comments: Number(row[5] || 0), shares: Number(row[6] || 0), averageViewDuration: Number(row[7] || 0) })),
        values: rows.map(row => Number(row[1] || 0))
      };
    } catch {
      // Analytics may not be available until the API is enabled and access is approved.
    }
    return NextResponse.json({
      connected: true,
      channel: {
        id: channel?.id,
        title: channel?.title,
        thumbnail: channel?.thumbnail,
        subscribers: channel?.subscribers ? Number(channel.subscribers).toLocaleString("en-US") : "—",
        views: channel?.views ? Number(channel.views).toLocaleString("en-US") : "—",
        videos: channel?.videos ?? "—"
      },
      analytics
    });
  } catch {
    return NextResponse.json(empty);
  }
}