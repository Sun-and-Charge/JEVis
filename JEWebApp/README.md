# JEVis web app

The web version of the JEVis Control Center's view plugins (analyses, dashboards, later the ISO 50001 modules).
Vue 3 + TypeScript + Vite. The built app is served by JEWebService at `/app/`; it uses the REST API of the
same server (`/JEWebService/v1`), so no CORS setup is needed.

Status: login (incl. two-factor code, "remember me", forgot/change password), navigation by organisation and
building, language switch (de/en) (phase W1); analyses with time frame, aggregation and manipulation, all chart
types of the Control Center (Apache ECharts), summary tables, PNG/CSV export (phase W2). Dashboards (W3) follow.

The analysis view (`src/views/AnalysisView.vue`) keeps its settings in the URL (`?tf=LAST_MONTH&agg=DAILY&man=NONE`,
with `tf=CUSTOM` also `from`/`to` as ISO 8601). The mapping of the API answer to charts, tables and CSV is in plain
TypeScript modules under `src/analysis/` (tested in `tests/analysis.test.ts`).

## Requirements

- Node.js 24 LTS (or 22.12+) and npm, only for building. The server needs no Node.js.

## Development

```bash
cd JEWebApp
npm ci                 # exact versions from package-lock.json
npm run dev            # http://localhost:5173/app/, API calls go to http://127.0.0.1:8090
```

The API target of the dev server can be changed with `JEVIS_WS=https://jevis.example.org npm run dev`.

```bash
npm test               # unit tests (Vitest)
npm run typecheck      # vue-tsc
npm run build          # type check + production build into dist/
```

## Deployment

1. `npm ci && npm run build` (on the build machine).
2. Copy `dist/` to the server, e.g. `/home/jevis/jevis/JEWebApp/dist`.
3. In `etc/webservice.xml`, inside `<webservice>`:

   ```xml
   <webapp>/home/jevis/jevis/JEWebApp/dist</webapp>
   ```

4. Restart the web service. The app is at `https://<server>/app/`; the start page (`<webpage>`) links to it.

`index.html` is never cached, the files in `assets/` have a content hash and are cached for a year, so a new
build is picked up by the browsers right away.

## Login and tokens

The app uses the token login of the web service (`/auth/login`, `/auth/login/mfa`, `/auth/refresh`,
`/auth/logout`). The access token is kept in memory and, without "remember me", in `sessionStorage` (survives a
reload of the tab). With "remember me" only the refresh token is stored (`localStorage`) and used to get a new
access token. Users who must set up two-factor authentication are asked to do this in the Control Center.

## Licenses

The app is GPL-3.0-only. `npm run build` writes `dist/THIRD-PARTY-LICENSES.txt` with the license texts of all
npm packages whose code is in the bundle (recorded by a small Vite plugin in `vite.config.ts`) and of the
Montserrat font (SIL OFL 1.1, `public/fonts`). New dependencies: install with
`npm install --before=<date two weeks ago> <package>` and check the license before using them.
