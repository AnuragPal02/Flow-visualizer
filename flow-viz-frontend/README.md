# flow-viz-frontend

React + Vite + React Flow UI for the microservice call-graph visualizer.
Consumes the backend's `POST /analyze` and renders an interactive, auto-laid-out
flow diagram per endpoint.

Verified: `npm install && npm run build` succeeds clean (checked before handing
this over — 0 errors, 528 modules).

## Requirements
- Node.js 18+

## Setup
```
npm install
npm run dev
```
Opens on http://localhost:5173.

## Connecting to the backend
Defaults to `http://localhost:8080` (the Spring Boot backend's default port).
If yours runs elsewhere, copy `.env.example` to `.env` and change
`VITE_API_BASE`.

The backend needs `@CrossOrigin(origins = "*")` (already set on
`AnalyzeController` in the backend project) for this to work from a different
port during local dev — that's fine for the hackathon demo, tighten it before
this goes anywhere real.

## How to use it
1. Paste the TARGET repo's `src/main/java` absolute path into the form
   (this path must exist on the machine running the **backend**, not
   necessarily this frontend, if they're on different machines).
2. Paste in the classpath string from `mvn dependency:build-classpath`
   (optional, but you'll get much better node classification with it —
   see the backend README).
3. Hit **Analyze Repo**. Discovered endpoints show up in the sidebar list.
4. Click an endpoint to render its flow. Click any node in the canvas to see
   its file path, type, and framework in the side panel.

## Structure
```
src/
├── App.jsx                    # top-level state + layout
├── api.js                     # fetch wrapper for POST /analyze
├── components/
│   ├── RepoInputForm.jsx       # repo path + classpath form
│   ├── EndpointList.jsx        # sidebar endpoint picker
│   ├── FlowCanvas.jsx          # React Flow canvas, converts graph JSON → nodes/edges
│   ├── CustomNode.jsx          # color-coded node (by NodeType)
│   └── NodeDetailsPanel.jsx    # click-through side panel
└── layout/
    └── dagreLayout.js          # auto-layout — React Flow has none built in
```

## Known limitations (matches the backend's scope for a 1-day build)
- No repo browsing/picker — you paste an absolute path, there's no file
  browser or git-clone-from-URL flow.
- Node summaries only render if the backend's (optional) LLM enrichment step
  is wired in — otherwise the details panel just says so.
- Single endpoint view only — no side-by-side comparison of two endpoints.
