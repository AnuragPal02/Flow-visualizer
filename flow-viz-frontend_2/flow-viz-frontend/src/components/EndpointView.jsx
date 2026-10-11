import { useState } from "react";
import Contract from "./Contract.jsx";
import Glance from "./Glance.jsx";
import StepList from "./StepList.jsx";
import { copyText, toMarkdown, toMermaid } from "../lib/export.js";

export default function EndpointView({
  graph,
  index,
  usage,
  panel,
  onOpenClass,
  onOpenCall,
  onOpenLine,
  onSelectEndpoint,
}) {
  const [copied, setCopied] = useState("");

  async function copy(kind) {
    const ok = await copyText(kind === "mermaid" ? toMermaid(graph) : toMarkdown(graph));
    setCopied(ok ? kind : "failed");
    setTimeout(() => setCopied(""), 1600);
  }

  const label = (kind, text) => (copied === kind ? "Copied" : copied === "failed" ? "Copy failed" : text);

  return (
    <div className="flow">
      <header className="flow-head">
        <div className="flow-title">
          <span className="badge large" data-m={graph.httpMethod}>
            {graph.httpMethod}
          </span>
          <h2>{graph.path}</h2>
        </div>
        <div className="flow-actions">
          <button type="button" className="ghost" onClick={() => copy("mermaid")} title="Copy a Mermaid diagram of this flow">
            {label("mermaid", "Copy Mermaid")}
          </button>
          <button type="button" className="ghost" onClick={() => copy("markdown")} title="Copy this flow as Markdown">
            {label("markdown", "Copy Markdown")}
          </button>
        </div>
      </header>

      <Contract graph={graph} />
      <Glance summary={graph.summary} />

      <h3 className="section-h">Flow</h3>
      <p className="flow-hint">
        Read from top to bottom. Each arrow shows the line that makes the next call; click it to see that line. The
        arrow on the right of a step opens its class.
      </p>

      <StepList
        graph={graph}
        index={index}
        usage={usage}
        panel={panel}
        onOpenClass={onOpenClass}
        onOpenCall={onOpenCall}
        onOpenLine={onOpenLine}
        onSelectEndpoint={onSelectEndpoint}
      />
    </div>
  );
}
