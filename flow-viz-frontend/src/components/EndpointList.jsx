export default function EndpointList({ endpoints, selectedIndex, onSelect }) {
  if (!endpoints || endpoints.length === 0) return null;

  return (
    <div className="endpoint-list">
      <div className="endpoint-list-header">{endpoints.length} endpoint(s) found</div>
      {endpoints.map((ep, i) => (
        <button
          key={i}
          className={`endpoint-item ${i === selectedIndex ? 'active' : ''}`}
          onClick={() => onSelect(i)}
        >
          <span className={`http-badge http-${ep.httpMethod.toLowerCase()}`}>{ep.httpMethod}</span>
          <span className="endpoint-path">{ep.path}</span>
          <span className="framework-badge">{ep.framework}</span>
        </button>
      ))}
    </div>
  );
}
