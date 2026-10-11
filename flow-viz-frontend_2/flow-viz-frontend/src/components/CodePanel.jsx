import { useEffect, useMemo, useRef } from "react";
import { highlightJava } from "../lib/highlight.js";

/**
 * Full source of one file. `panel` says what to emphasise:
 *  - methodRange [from, to]: the method the step belongs to (soft highlight)
 *  - callLine: the exact line that makes the call (strong highlight)
 *  - scrollTo: the line to bring into view
 */
export default function CodePanel({ panel, source, onClose }) {
  const bodyRef = useRef(null);
  const lines = useMemo(() => (source ? highlightJava(source) : []), [source]);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  useEffect(() => {
    const body = bodyRef.current;
    if (!body) return;
    const target = body.querySelector('[data-target="true"]');
    if (target) target.scrollIntoView({ block: "center" });
    else body.scrollTop = 0;
  }, [panel, source]);

  const [methodFrom, methodTo] = panel.methodRange || [0, 0];

  return (
    <aside className="code-panel" aria-label="Source code">
      <header className="code-head">
        <div className="code-head-text">
          <div className="code-title">{panel.title}</div>
          <div className="code-file">{panel.file}</div>
          <div className="code-sub">{panel.subtitle}</div>
        </div>
        <button type="button" className="code-close" onClick={onClose} aria-label="Close code panel" title="Close (Esc)">
          {"\u00d7"}
        </button>
      </header>

      <div className="code-body" ref={bodyRef}>
        {lines.length === 0 ? (
          <div className="code-empty">Source is not available for this file.</div>
        ) : (
          lines.map((tokens, i) => {
            const n = i + 1;
            const inMethod = methodFrom > 0 && n >= methodFrom && n <= methodTo;
            const isCall = n === panel.callLine;
            return (
              <div
                key={n}
                className={"code-line" + (inMethod ? " in-method" : "") + (isCall ? " is-call" : "")}
                data-target={n === panel.scrollTo ? "true" : undefined}
              >
                <span className="code-ln">{n}</span>
                <span className="code-text">
                  {tokens.length === 0
                    ? " "
                    : tokens.map((t, j) =>
                        t.cls ? (
                          <span key={j} className={"tok-" + t.cls}>
                            {t.text}
                          </span>
                        ) : (
                          t.text
                        ),
                      )}
                </span>
              </div>
            );
          })
        )}
      </div>
    </aside>
  );
}
