import { authorizedYouTube, apiError } from "../../../../lib/youtube-server";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    const yt = await authorizedYouTube(request);
    const channel = await yt.google("youtube/v3/channels?part=contentDetails&mine=true");
    const playlistId = channel.items?.[0]?.contentDetails?.relatedPlaylists?.uploads;
    if (!playlistId) return Response.json({ items: [], nextPageToken: null });
    const pageToken = new URL(request.url).searchParams.get("pageToken");
    const params = new URLSearchParams({ part: "snippet,contentDetails", playlistId, maxResults: "50" });
    if (pageToken) params.set("pageToken", pageToken);
    const page = await yt.google("youtube/v3/playlistItems?" + params.toString());
    const ids = (page.items || []).map((item: any) => item.contentDetails?.videoId).filter(Boolean);
    if (!ids.length) return Response.json({ items: [], nextPageToken: page.nextPageToken || null });
    const detail = await yt.google("youtube/v3/videos?" + new URLSearchParams({ part: "snippet,statistics,status,contentDetails", id: ids.join(",") }).toString());
    const byId = new Map((detail.items || []).map((v: any) => [v.id, v]));
    return Response.json({ items: ids.map((id: string) => byId.get(id)).filter(Boolean), nextPageToken: page.nextPageToken || null }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) { return apiError(error); }
}
export async function PATCH(request: Request) {
  try {
    const yt = await authorizedYouTube(request);
    const body = await request.json();
    const id = String(body.id || "");
    const title = String(body.title || "").trim();
    const description = String(body.description || "");
    const privacyStatus = String(body.privacyStatus || "private");
    if (!id || !title) return apiError(new Error("A video ID and title are required."));
    if (!["public", "unlisted", "private"].includes(privacyStatus)) return apiError(new Error("Invalid visibility setting."));
    const current = await yt.google("youtube/v3/videos?" + new URLSearchParams({ part: "snippet,status", id }).toString());
    const video = current.items?.[0];
    if (!video) return apiError(new Error("Video not found on your channel."), 404);
    const item = await yt.google("youtube/v3/videos?part=snippet,status", { method: "PUT", body: JSON.stringify({ id, snippet: { ...video.snippet, title: title.slice(0,100), description: description.slice(0,5000) }, status: { ...video.status, privacyStatus } }) });
    return Response.json({ item }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) { return apiError(error); }
}
