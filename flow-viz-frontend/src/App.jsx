import { useState } from 'react';
import RepoInputForm from './components/RepoInputForm.jsx';
import EndpointList from './components/EndpointList.jsx';
import FlowCanvas from './components/FlowCanvas.jsx';
import { analyzeRepo } from './api.js';

export default function App() {
  const [endpoints, setEndpoints] = useState([]);
  const [selectedIndex, setSelectedIndex] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [warnings, setWarnings] = useState([]);

  async function handleAnalyze(sourcePath, classpath) {
    setLoading(true);
    setError(null);
    try {
      const result = await analyzeRepo(sourcePath, classpath);
      const found = result.endpoints || [];
      setEndpoints(found);
      setWarnings(result.warnings || []);
      setSelectedIndex(found.length > 0 ? 0 : null);
    } catch (err) {
      setError(err.message);
      setEndpoints([]);
      setSelectedIndex(null);
    } finally {
      setLoading(false);
    }
  }

  const selectedGraph = selectedIndex !== null ? endpoints[selectedIndex] : null;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <h1>Flow Visualizer</h1>
        <RepoInputForm onAnalyze={handleAnalyze} loading={loading} error={error} />
        <EndpointList endpoints={endpoints} selectedIndex={selectedIndex} onSelect={setSelectedIndex} />
        {warnings.length > 0 && (
          <details className="warnings-box">
            <summary>{warnings.length} warning(s)</summary>
            <ul>
              {warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          </details>
        )}
      </aside>
      <main className="main-area">
        <FlowCanvas graph={selectedGraph} />
      </main>
    </div>
  );
}
