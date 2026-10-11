import { typeLabel, kindLabel } from "../lib/labels.js";

/** The answers a developer wants first: what data is touched, who is called, what behaviour applies. */
export default function Glance({ summary }) {
  if (!summary) return null;
  const noData = !summary.reads.length && !summary.writes.length && !summary.touches.length;

  return (
    <section className="glance" aria-label="At a glance">
      <Cell title="Flow">
        <div className="glance-big">
          {summary.steps} step{summary.steps === 1 ? "" : "s"}
        </div>
        <div className="glance-sub">{summary.layers.map(typeLabel).join(" \u2192 ")}</div>
      </Cell>

      <Cell title="Your data">
        {noData ? (
          <div className="glance-sub">No repository access in this flow.</div>
        ) : (
          <>
            <Row label="Reads" items={summary.reads} />
            <Row label="Writes" items={summary.writes} />
            <Row label="Touches" items={summary.touches} />
          </>
        )}
      </Cell>

      <Cell title="Calls outside your code">
        {summary.integrations.length === 0 ? (
          <div className="glance-sub">Nothing detected.</div>
        ) : (
          <div className="chips">
            {summary.integrations.map((i) => (
              <span key={i.kind + i.client} className="chip chip-outside">
                {kindLabel(i.kind)} {"\u00b7"} {i.client}
                {i.calls > 1 ? ` \u00d7${i.calls}` : ""}
              </span>
            ))}
          </div>
        )}
      </Cell>

      <Cell title="Behaviour">
        <div className="chips">
          <span className="chip">{summary.transactional ? "Transactional" : "Not transactional"}</span>
          {summary.security.map((s) => (
            <span key={s} className="chip">
              {s}
            </span>
          ))}
        </div>
        {summary.security.length === 0 && (
          <div className="glance-sub">No method-level security annotation found.</div>
        )}
      </Cell>
    </section>
  );
}

function Cell({ title, children }) {
  return (
    <div className="glance-cell">
      <div className="glance-title">{title}</div>
      {children}
    </div>
  );
}

function Row({ label, items }) {
  if (!items.length) return null;
  return (
    <div className="glance-row">
      <span className="glance-label">{label}</span>
      <span className="chips">
        {items.map((item) => (
          <span key={item} className="chip chip-data">
            {item}
          </span>
        ))}
      </span>
    </div>
  );
}
