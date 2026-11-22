# HighVibe frontend

React 18 + TypeScript + Vite + Tailwind CSS 4.

```bash
npm install
npm run dev      # http://localhost:5173, proxies /api to the gateway on :8080
npm run build    # type-check + production build into dist/
```

| Path | What it does |
| --- | --- |
| `src/lib/api.ts` | `authFetch` / `api` helpers: adds the Bearer token, refreshes it once on 401, turns errors into `ApiError` |
| `src/lib/auth.tsx` | Auth context: login, signup, logout, current user |
| `src/lib/stream.ts` | Reads the AI server-sent events with `fetch` (EventSource cannot send headers) |
| `src/lib/parse.ts` | Turns the streamed `<message>`, `<tool>`, `<file>` tags into chat events while they arrive |
| `src/pages/HomePage.tsx` | Prompt box, project list, invitations |
| `src/pages/WorkspacePage.tsx` | Chat + live preview + code editor for one project |
| `src/pages/BillingPage.tsx` | Plans, usage and checkout |
| `vite.config.ts` / `nginx.conf` | Dev proxy / production proxy of `/api` to the gateway |

Set `VITE_GATEWAY_URL` to point the dev proxy at a different gateway.
