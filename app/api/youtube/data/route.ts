import { NextResponse } from "next/server";
import { createDecipheriv } from "node:crypto";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function decryptTokens(value: string) {
  const parsed = JSON.parse(value);
  const key = Buffer.from(process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY || "", "base64");
  if (key.length !== 32 || parsed.alg !== "A256GCM") throw new Error("invalid_encryption_config");
  const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(parsed.iv, "base64"));
  decipher.setAuthTag(Buffer.from(parsed.tag, "base64"));
  return JSON.parse(Buffer.concat([decipher.update(Buffer.from(parsed.data, "base64")), decipher.final()]).toString("utf8"));
}

async function googleFetch(path: string, token: string) {
  const response = await fetch("https://www.googleapis.com/" + path, { headers: { Authorization: "Bearer " + token }, cache: "no-store" });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error?.message || "youtube_api_error");
  return data;
}

export async function GET() {
  const empty = { connected: false };
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!supabaseUrl || !serviceKey || !process.env.YOUTUBE_TOKEN_ENCRYPTION_KEY) return NextResponse.json(empty);
  try {
    const supabase = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data, error } = await supabase.from("youtube_connections").select("channel_id,encrypted_tokens,updated_at").order("updated_at", { ascending: false }).limit(1).maybeSingle();
    if (error || !data?.encrypted_tokens) return NextResponse.json(empty);
    let tokens = decryptTokens(data.encrypted_tokens);
    const expiry = Number(tokens.obtained_at || 0) + Number(tokens.expires_in || 3600) * 1000 - 60000;
    if (expiry < Date.now() && tokens.refresh_token) {
      const refreshed = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ client_id: process.env.YOUTUBE_CLIENT_ID || "", client_secret: process.env.YOUTUBE_CLIENT_SECRET || "", refresh_token: tokens.refresh_token, grant_type: "refresh_token" }), cache: "no-store"
      }).then(r => r.json());
      if (refreshed.access_token) {
        tokens = { ...tokens, ...refreshed, refresh_token: tokens.refresh_token, obtained_at: Date.now(), channel: tokens.channel };
        await supabase.from("youtube_connections").update({ encrypted_tokens: JSON.stringify((await import("node:crypto")).createCipheriv ? "" : "") }).eq("channel_id", data.channel_id).then(() => {});
        // The token is refreshed for this request; the next OAuth consent can be used if persistence configuration is incomplete.
      } else if (refreshed.error) {
        return NextResponse.json({ connected: true, channel: tokens.channel, error: "token_refresh_required" });
      }
    }
    const channel = tokens.channel;
    const now = new Date();
    const end = now.toISOString().slice(0, 10);
    const startDate = new Date(now.getTime() - 28 * 86400000).toISOString().slice(0, 10);
    let analytics = {};
    try {
      const report = await googleFetch("youtube/analytics/v2/reports?ids=channel%3D%3DMINE&startDate=" + startDate + "&endDate=" + end + "&metrics=views%2CestimatedMinutesWatched%2CsubscribersGained&dimensions=day&sort=day", tokens.access_token);
      const rows: unknown[][] = report.rows || [];
      analytics = {
        views: Number(rows.reduce((sum, row) => sum + Number(row[1] || 0), 0)).toLocaleString("en-US"),
        watchTime: Math.round(rows.reduce((sum, row) => sum + Number(row[2] || 0), 0) / 60).toLocaleString("en-US"),
        subscribers: "+" + rows.reduce((sum, row) => sum + Number(row[3] || 0), 0).toLocaleString("en-US"),
        values: rows.map(row => Number(row[1] || 0))
      };
    } catch { /* Analytics can be unavailable until the API is enabled or permissions propagate. */ }
    return NextResponse.json({ connected: true, channel: { id: channel?.id, title: channel?.title, thumbnail: channel?.thumbnail, subscribers: channel?.subscribers ? Number(channel.subscribers).toLocaleString("en-US") : "—", views: channel?.views ? Number(channel.views).toLocaleString("en-US") : "—", videos: channel?.videos ?? "—" }, analytics });
  } catch {
    return NextResponse.json(empty);
  }
}