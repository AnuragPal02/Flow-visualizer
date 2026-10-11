import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import RepoInputForm from "./components/RepoInputForm.jsx";
import EndpointList from "./components/EndpointList.jsx";
import EndpointView from "./components/EndpointView.jsx";
import CodePanel from "./components/CodePanel.jsx";
import { analyzeRepo } from "./api.js";
import { buildUsage } from "./lib/usage.js";

export default function App() {
  const [endpoints, setEndpoints] = useState([]);
  const [sources, setSources] = useState({});
  const [warnings, setWarnings] = useState([]);
  const [classCount, setClassCount] = useState(0);
  const [selectedIndex, setSelectedIndex] = useState(null);
  const [panel, setPanel] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [analyzed, setAnalyzed] = useState(false);
  const paneRef = useRef(null);

  const usage = useMemo(() => buildUsage(endpoints), [endpoints]);
  const closePanel = useCallback(() => setPanel(null), []);

  useEffect(() => {
    if (paneRef.current) paneRef.current.scrollTop = 0;
  }, [selectedIndex]);

  async function handleAnalyze(sourcePath) {
    setLoading(true);
    setError(null);
    setPanel(null);
    try {
      const result = await analyzeRepo(sourcePath);
      const found = result.endpoints || [];
      setEndpoints(found);
      setSources(result.sources || {});
      setWarnings(result.warnings || []);
      setClassCount(result.classesAnalyzed || 0);
      setSelectedIndex(found.length > 0 ? 0 : null);
      setAnalyzed(true);
    } catch (err) {
      setError(err.message);
      setEndpoints([]);
      setSources({});
      setWarnings([]);
      setSelectedIndex(null);
    } finally {
      setLoading(false);
    }
  }

  function selectEndpoint(index) {
    setSelectedIndex(index);
    setPanel(null);
  }

  // The arrow on the right of a step: the whole class, with the step's method highlighted.
  function openClass(node) {
    const key = "class:" + node.id;
    if (panel?.key === key) return setPanel(null);

    const hasBody = node.startLine > 0;
    setPanel({
      key,
      file: node.file,
      title: node.className,
      subtitle: hasBody
        ? `${node.methodName}() belongs to ${node.className} \u00b7 lines ${node.startLine} to ${node.endLine}`
        : `${node.methodName}() comes from the framework, so your code has no body for it. Showing the whole class.`,
      methodRange: hasBody ? [node.startLine, node.endLine] : null,
      callLine: 0,
      scrollTo: hasBody ? node.startLine : 1,
    });
  }

  // The call under an arrow: the caller's file with the calling line highlighted.
  function openCall(edge, caller) {
    const key = "call:" + edge.from + ">" + edge.to;
    if (panel?.key === key) return setPanel(null);

    setPanel({
      key,
      file: caller.file,
      title: caller.className,
      subtitle: `Line ${edge.line} of ${caller.methodName}() is where the next step is called`,
      methodRange: caller.startLine > 0 ? [caller.startLine, caller.endLine] : null,
      callLine: edge.line,
      scrollTo: edge.line,
    });
  }

  // A specific line inside a step, e.g. its outbound HTTP call.
  function openLine(node, line, subtitle) {
    const key = "line:" + node.id + ":" + line;
    if (panel?.key === key) return setPanel(null);

    setPanel({
      key,
      file: node.file,
      title: node.className,
      subtitle,
      methodRange: node.startLine > 0 ? [node.startLine, node.endLine] : null,
      callLine: line,
      scrollTo: line,
    });
  }

  const selected = selectedIndex !== null ? endpoints[selectedIndex] : null;
  const emptyMessage =
    analyzed && endpoints.length === 0
      ? "No REST endpoints were found in this folder."
      : "Enter a source folder and click Analyze Repo.";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <h1>Flow Visualizer</h1>
          <p className="tagline">See how your application classes connect.</p>
        </div>
        <RepoInputForm onAnalyze={handleAnalyze} loading={loading} error={error} />
        {endpoints.length > 0 && (
          <div className="stats">
            {endpoints.length} endpoints {"\u00b7"} {classCount} classes analyzed
          </div>
        )}
        <EndpointList endpoints={endpoints} selectedIndex={selectedIndex} onSelect={selectEndpoint} />
        {warnings.length > 0 && (
          <details className="warnings">
            <summary>{warnings.length} warning(s)</summary>
            <ul>
              {warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          </details>
        )}
      </aside>

      <main className="main">
        <section className="flow-pane" ref={paneRef}>
          {selected ? (
            <EndpointView
              graph={selected}
              index={selectedIndex}
              usage={usage}
              panel={panel}
              onOpenClass={openClass}
              onOpenCall={openCall}
              onOpenLine={openLine}
              onSelectEndpoint={selectEndpoint}
            />
          ) : (
            <div className="empty">{emptyMessage}</div>
          )}
        </section>

        {panel && <CodePanel panel={panel} source={sources[panel.file]} onClose={closePanel} />}
      </main>
    </div>
  );
}
