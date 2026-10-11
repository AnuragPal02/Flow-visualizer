# flow-viz-frontend

Dark, blue-and-black UI for the Flow Visualizer backend.

    npm install
    npm run dev        # http://localhost:5173, backend expected on http://localhost:8080

1. Enter the target `src/main/java` folder (or the repo folder) and click **Analyze Repo**.
2. Search or pick an endpoint.
3. Top of the page: the request/response contract and an at-a-glance row (data read/written, calls outside your
   code, transaction, security).
4. Below: the flow. Under each arrow is the line that makes the call; click it to see that line. The arrow on the
   right of a step opens its class. "Also used by N other endpoints" shows what else depends on a step.
5. **Copy Mermaid** / **Copy Markdown** put the flow on your clipboard for a PR or docs. Esc closes the code panel.

Backend elsewhere? Copy `.env.example` to `.env` and set `VITE_API_BASE`.
