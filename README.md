# Ytdash — Creator Studio

A responsive, dark Apple-inspired YouTube creator dashboard built with Next.js, React, TypeScript and Supabase.

## Included

- Responsive dark creator workspace with Dashboard, Analytics, Content, Comments and Settings
- Real authorized channel totals and YouTube Analytics daily reporting
- Live upload-playlist video listing with thumbnails, statistics, visibility and publish dates
- Edit video title, description and visibility through YouTube Data API
- Start resumable uploads with title, description and privacy selection
- Load comment threads, reply to comments, hold comments for review and delete comments
- Google OAuth with state validation and PKCE; server-side token refresh and AES-256-GCM encrypted token storage
- Mobile navigation, content search and visibility filters; no fabricated channel metrics
- GitHub Actions type-check/build verification

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

## Integration boundaries and deployment checklist

- The app uses Google's public YouTube Data API and YouTube Analytics API. It is not Google's private YouTube Studio backend and cannot reproduce features that Google does not expose to third-party applications.
- YouTube Analytics may return no rows until the API is enabled and access is available for the channel. Recent dates may have reporting delays.
- Resumable upload uses a browser-to-YouTube upload session. The upload session URI is temporary and must not be saved or shared.
- Before sign-in works on Vercel, configure all server environment variables in the `ytdash` Vercel project: `NEXT_PUBLIC_SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_REDIRECT_URI`, and `YOUTUBE_TOKEN_ENCRYPTION_KEY`. Do not put service-role keys or client secrets in `NEXT_PUBLIC_` variables.
- Set `YOUTUBE_REDIRECT_URI` to `https://ytdash-z-hub.vercel.app/api/youtube/callback` and add the exact same URL to the Google OAuth Web client. Enable YouTube Data API v3 and YouTube Analytics API in Google Cloud.
- After granting the expanded upload/comment permissions, reconnect the channel so Google issues a token with the new scopes. OAuth consent-screen verification/testing-user settings may be required depending on who will use the app.
- This is an independent app, not an official Google product. Never commit secrets.
