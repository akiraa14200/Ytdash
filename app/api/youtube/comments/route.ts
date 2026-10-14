import { authorizedYouTube, apiError } from "../../../../lib/youtube-server";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    const yt = await authorizedYouTube(request);
    const url = new URL(request.url);
    const params = new URLSearchParams({ part: "snippet,replies", allThreadsRelatedToChannel: "true", maxResults: "50", order: url.searchParams.get("order") === "relevance" ? "relevance" : "time", textFormat: "plainText" });
    const pageToken = url.searchParams.get("pageToken");
    if (pageToken) params.set("pageToken", pageToken);
    const data = await yt.google("youtube/v3/commentThreads?" + params.toString());
    return Response.json({ items: data.items || [], nextPageToken: data.nextPageToken || null }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) { return apiError(error); }
}
export async function POST(request: Request) {
  try {
    const yt = await authorizedYouTube(request);
    const body = await request.json();
    const action = String(body.action || "");
    const commentId = String(body.commentId || "");
    if (action === "reply") {
      const text = String(body.text || "").trim();
      if (!commentId || !text) return apiError(new Error("Choose a comment and enter a reply."));
      const item = await yt.google("youtube/v3/comments?part=snippet", { method: "POST", body: JSON.stringify({ snippet: { parentId: commentId, textOriginal: text.slice(0,10000) } }) });
      return Response.json({ success: true, item });
    }
    if (!commentId) return apiError(new Error("A comment ID is required."));
    if (action === "delete") {
      await yt.google("youtube/v3/comments?id=" + encodeURIComponent(commentId), { method: "DELETE" });
      return Response.json({ success: true });
    }
    if (action === "hold" || action === "publish") {
      const status = action === "hold" ? "heldForReview" : "published";
      await yt.google("youtube/v3/comments/setModerationStatus?" + new URLSearchParams({ id: commentId, moderationStatus: status }), { method: "POST" });
      return Response.json({ success: true });
    }
    return apiError(new Error("Unsupported comment action."));
  } catch (error) { return apiError(error); }
}
