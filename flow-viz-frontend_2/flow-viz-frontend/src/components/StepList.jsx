import { useMemo, useState } from "react";
import { buildSteps } from "../steps.js";
import { typeLabel, kindLabel } from "../lib/labels.js";
import { otherEndpoints } from "../lib/usage.js";

const INDENT = 22;
const MAX_INDENT = 4;

export default function StepList({
  graph,
  index,
  usage,
  panel,
  onOpenClass,
  onOpenCall,
  onOpenLine,
  onSelectEndpoint,
}) {
  const steps = useMemo(() => buildSteps(graph), [graph]);

  return (
    <ol className="steps">
      {steps.map((step, i) => (
        <li
          key={step.node.id}
          className="step"
          style={{ marginLeft: Math.min(step.depth, MAX_INDENT) * INDENT }}
        >
          {step.via && <Connector step={step} panel={panel} onOpenCall={onOpenCall} />}
          <StepCard
            number={i + 1}
            step={step}
            active={panel?.key === "class:" + step.node.id}
            others={otherEndpoints(usage, step.node.id, index)}
            onOpen={() => onOpenClass(step.node)}
            onOpenLine={onOpenLine}
            onSelectEndpoint={onSelectEndpoint}
          />
        </li>
      ))}
    </ol>
  );
}

function StepCard({ number, step, active, others, onOpen, onOpenLine, onSelectEndpoint }) {
  const n = step.node;
  const providedByFramework = n.startLine === 0;
  const label = "Show the code of " + n.className;

  return (
    <div className={"card" + (active ? " active" : "")}>
      <div className="card-main">
        <div className="card-top">
          <span className="step-no">{number}</span>
          <span className="pill">{typeLabel(n.type)}</span>
        </div>

        <div className="card-method">{n.methodName}()</div>
        <div className="card-belongs">
          method of <strong>{n.className}</strong>
          {!providedByFramework && ` \u00b7 lines ${n.startLine}-${n.endLine}`}
        </div>

        {n.signature && <code className="card-sig">{n.signature}</code>}
        {n.doc && <p className="card-doc">{n.doc}</p>}

        <Facts node={n} onOpenLine={onOpenLine} />

        {n.alternatives && n.alternatives.length > 0 && (
          <p className="card-note">
            Decided at runtime. Implementations in your code: {n.alternatives.join(", ")}.
          </p>
        )}
        {providedByFramework && n.type === "REPOSITORY" && (
          <p className="card-note">
            Provided by the framework (for example Spring Data). Your code only declares the repository.
          </p>
        )}
        {!step.via && <p className="card-note">The request starts here.</p>}

        <div className="card-file">{n.file}</div>
        <UsedBy others={others} onSelect={onSelectEndpoint} />
      </div>

      <button
        type="button"
        className="card-open"
        onClick={onOpen}
        aria-label={label}
        aria-pressed={active}
        title={label}
      >
        <span aria-hidden="true">{"\u203a"}</span>
      </button>
    </div>
  );
}

/** Data access, behaviour tags and outbound calls of one step, as small chips. */
function Facts({ node, onOpenLine }) {
  const chips = [];

  if (node.type === "REPOSITORY" && node.entity) {
    const verb = node.access === "READ" ? "Reads" : node.access === "WRITE" ? "Writes" : "Touches";
    chips.push(
      <span key="data" className="chip chip-data">
        {verb} {node.entity}
      </span>,
    );
  }
  (node.tags || []).forEach((tag, i) =>
    chips.push(
      <span key={"tag" + i} className="chip">
        {tag.text}
      </span>,
    ),
  );
  (node.integrations || []).forEach((it, i) => {
    const isCall = Boolean(it.code);
    const text = isCall
      ? `${kindLabel(it.kind)} \u00b7 ${it.client}.${it.detail}()`
      : `${it.client}${it.detail ? " \u00b7 " + it.detail : ""}`;
    if (isCall && it.line > 0) {
      chips.push(
        <button
          key={"int" + i}
          type="button"
          className="chip chip-action"
          title={`Show line ${it.line}`}
          onClick={() => onOpenLine(node, it.line, `${it.client}.${it.detail}() is called on line ${it.line}`)}
        >
          {text} <span className="chip-line">line {it.line}</span>
        </button>,
      );
    } else {
      chips.push(
        <span key={"int" + i} className="chip chip-outside">
          {text}
        </span>,
      );
    }
  });

  if (chips.length === 0 && !node.query) return null;
  return (
    <div className="facts">
      {chips.length > 0 && <div className="chips">{chips}</div>}
      {node.query && <pre className="query">{node.query}</pre>}
    </div>
  );
}

/** "Also used by 3 other endpoints": what else breaks if this method changes. */
function UsedBy({ others, onSelect }) {
  const [open, setOpen] = useState(false);
  if (others.length === 0) return null;

  return (
    <div className="usedby">
      <button type="button" className="usedby-toggle" onClick={() => setOpen(!open)} aria-expanded={open}>
        Also used by {others.length} other endpoint{others.length === 1 ? "" : "s"} {open ? "\u25b4" : "\u25be"}
      </button>
      {open && (
        <ul>
          {others.map((o) => (
            <li key={o.index}>
              <button type="button" onClick={() => onSelect(o.index)}>
                {o.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Connector({ step, panel, onOpenCall }) {
  const calls = [
    { edge: step.via, caller: step.caller, primary: true },
    ...step.otherCalls.map((c) => ({ ...c, primary: false })),
  ];

  return (
    <div className="connector">
      <div className="connector-rail" aria-hidden="true">
        <span className="connector-arrow">{"\u2193"}</span>
      </div>
      <div className="connector-calls">
        {calls.map(({ edge, caller, primary }) => {
          const key = "call:" + edge.from + ">" + edge.to;
          return (
            <Call
              key={key}
              edge={edge}
              caller={caller}
              primary={primary}
              active={panel?.key === key}
              onOpen={() => onOpenCall(edge, caller)}
            />
          );
        })}
      </div>
    </div>
  );
}

function Call({ edge, caller, primary, active, onOpen }) {
  const clickable = edge.line > 0;
  return (
    <button
      type="button"
      className={"call" + (primary ? "" : " secondary") + (active ? " active" : "")}
      onClick={onOpen}
      disabled={!clickable}
      title={clickable ? "Show this line in the code" : undefined}
    >
      <span className="call-text">
        {primary ? "Called by " : "Also called by "}
        <strong>
          {caller.className}.{caller.methodName}()
        </strong>
        {clickable && (
          <>
            {" at line "}
            <strong>{edge.line}</strong>
          </>
        )}
      </span>
      {edge.code && <code className="call-code">{edge.code}</code>}
    </button>
  );
}
