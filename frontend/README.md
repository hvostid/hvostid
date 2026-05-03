# Frontend

React 18 single-page app served via Vite in development and Nginx in
production.

## Responsibilities

- Auth flows (register, login, logout, profile).
- Catalog browsing and listing detail.
- Seller pages (create / edit / list own listings).
- Buyer questionnaire and matching results.
- Moderator panel (queue, listing detail, flags).

## Stack

- React 18 + React Router 6
- Vite 6
- Tailwind CSS 3
- Axios
- ESLint 9 (flat config) + Prettier 3, with the project's pre-commit
  hook auto-fixing staged files

## Layout

```
src/
  api/             -- axios client + interceptors (token, refresh)
  context/         -- AuthContext
  components/      -- shared components (Navbar, ProtectedRoute)
  pages/           -- one file per route
  index.css        -- Tailwind entry
  main.jsx         -- app bootstrap
  App.jsx          -- router
```

## Environment

The dev server proxies `/api` to `http://localhost:8080` (the gateway).
That mapping is in
[`vite.config.js`](./vite.config.js); to point the SPA at a different
backend, edit the proxy target.

The production Nginx image (built via [`Dockerfile`](./Dockerfile))
serves the static bundle and is fronted by the gateway in the Compose
stack.

## Run locally

```bash
npm install
npm run dev      # Vite on http://localhost:3000, proxies /api -> :8080
```

## Scripts

| Script                  | Purpose                                     |
|-------------------------|---------------------------------------------|
| `npm run dev`           | Vite dev server                             |
| `npm run build`         | Production bundle to `dist/`                |
| `npm run preview`       | Preview the built bundle                    |
| `npm run lint`          | ESLint                                      |
| `npm run lint:fix`      | ESLint with auto-fix                        |
| `npm run format`        | Prettier write                              |
| `npm run format:check`  | Prettier check (CI)                         |

## Dependencies

- **Required:** API Gateway reachable at the proxy target (default
  `http://localhost:8080`).
