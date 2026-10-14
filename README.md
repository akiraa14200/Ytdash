# Ytdash — Creator Studio

A responsive, dark Apple-inspired YouTube creator dashboard built with Next.js, React, TypeScript and Supabase.

## Included

- Responsive creator workspace with Dashboard, Analytics, Content, Comments and Settings views
- Mobile navigation, search, date-range toggle, notification popover and channel status
- Clearly labelled preview data when no channel is connected
- Google OAuth flow with state validation and PKCE
- Server-side YouTube channel lookup and YouTube Analytics API requests
- AES-256-GCM encryption for stored OAuth token payloads in the existing Supabase `youtube_connections` table
- GitHub Actions build verification

## Run locally

1. Install Node.js 20.9 or later.
2. Copy `.env.example` to `.env.local`.
3. Create a Google OAuth **Web application** credential in Google Cloud. Enable **YouTube Data API v3** and **YouTube Analytics API**. Add the exact redirect URI `http://localhost:3000/api/youtube/callback` to the OAuth client.
4. Fill `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_REDIRECT_URI`, `SUPABASE_SERVICE_ROLE_KEY` and `YOUTUBE_TOKEN_ENCRYPTION_KEY`. Keep secrets server-side; never prefix secrets with `NEXT_PUBLIC_`.
5. Generate a 32-byte encryption key and base64-encode it, for example with Node: `node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`.
6. Install and run:

```bash
npm install
npm run dev
```

Open http://localhost:3000.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Server-only Supabase service role key |
| `YOUTUBE_CLIENT_ID` | Google OAuth client ID |
| `YOUTUBE_CLIENT_SECRET` | Google OAuth client secret |
| `YOUTUBE_REDIRECT_URI` | OAuth callback URL, must match Google Cloud |
| `YOUTUBE_TOKEN_ENCRYPTION_KEY` | Base64-encoded 32-byte AES key |
| `APP_URL` | Public application URL |

The Supabase project must contain `public.youtube_connections(channel_id text primary key, encrypted_tokens text, updated_at timestamptz)`. Row-level security should remain enabled. Only the server uses the service-role key.

## Deploy to Vercel

Import `akiraa14200/Ytdash` into Vercel, configure all environment variables for the deployment environment, and set `YOUTUBE_REDIRECT_URI` to `https://YOUR_DOMAIN/api/youtube/callback`. Add that same callback URL to the Google OAuth client. Redeploy after changing environment variables.

## Current integration boundaries

- Views and channel statistics use the authorized channel's YouTube API data. Analytics may be empty until YouTube Analytics API access is enabled for the Google Cloud project.
- Content and comments currently provide the finished UI and preview rows; full list/edit/upload/reply/moderation operations are not yet wired to the YouTube API.
- This is an independent app, not an official Google product. Google OAuth verification may be required before allowing external users to authorize production scopes.
- Use a private repository for production source if you plan to keep implementation details private. Never commit secrets.
