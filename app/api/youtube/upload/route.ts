import { authorizedYouTube, apiError } from "../../../../lib/youtube-server";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export async function POST(request: Request) {
  try {
    const yt = await authorizedYouTube(request);
    const body = await request.json();
    const title = String(body.title || "").trim();
    const description = String(body.description || "");
    const mimeType = String(body.mimeType || "");
    const fileSize = Number(body.fileSize || 0);
    const privacyStatus = String(body.privacyStatus || "private");
    if (!title) return apiError(new Error("A video title is required."));
    if (!mimeType.startsWith("video/") || !Number.isFinite(fileSize) || fileSize <= 0) return apiError(new Error("Choose a valid video file."));
    if (!["public", "unlisted", "private"].includes(privacyStatus)) return apiError(new Error("Invalid visibility setting."));
    const metadata = { snippet: { title: title.slice(0,100), description: description.slice(0,5000) }, status: { privacyStatus, selfDeclaredMadeForKids: false } };
    const response = await fetch("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status", { method: "POST", headers: { Authorization: "Bearer " + yt.accessToken, "Content-Type": "application/json; charset=UTF-8", "X-Upload-Content-Type": mimeType, "X-Upload-Content-Length": String(fileSize) }, body: JSON.stringify(metadata), cache: "no-store" });
    if (!response.ok) { const error = await response.json().catch(() => ({})); return apiError(new Error(error.error?.message || "YouTube could not start the upload. Check API access and upload permissions."), response.status); }
    const uploadUrl = response.headers.get("location");
    if (!uploadUrl) return apiError(new Error("YouTube did not return an upload session."));
    return Response.json({ uploadUrl }, { headers: { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer" } });
  } catch (error) { return apiError(error); }
}
