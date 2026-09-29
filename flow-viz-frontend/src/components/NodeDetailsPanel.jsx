export default function NodeDetailsPanel({ node, onClose }) {
  if (!node) return null;
  const { data } = node;

  return (
    <div className="details-panel">
      <button className="details-close" onClick={onClose} aria-label="Close">
        ×
      </button>
      <h3>
        {data.className}.{data.methodName}()
      </h3>
      <div className="details-row">
        <span className="details-label">Type</span> {data.type}
      </div>
      <div className="details-row">
        <span className="details-label">Framework</span> {data.framework}
      </div>
      <div className="details-row">
        <span className="details-label">File</span> {data.file}
      </div>
      <div className="details-summary">
        {data.summary ? data.summary : <em>No summary generated yet — LLM enrichment is a stretch-goal step in the backend.</em>}
      </div>
    </div>
  );
}
