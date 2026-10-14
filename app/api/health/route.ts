import { NextResponse } from "next/server";
export function GET() {
  return NextResponse.json({ app: "Ytdash", status: "ok", timestamp: new Date().toISOString() });
}