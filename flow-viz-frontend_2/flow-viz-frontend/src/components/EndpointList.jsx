import { useState } from "react";

export default function EndpointList({ endpoints, selectedIndex, onSelect }) {
  const [query, setQuery] = useState("");
  if (!endpoints || endpoints.length === 0) return null;

  const q = query.trim().toLowerCase();
  const visible = endpoints
    .map((endpoint, index) => ({ endpoint, index }))
    .filter(({ endpoint }) => {
      if (!q) return true;
      const entry = endpoint.nodes[0] ? endpoint.nodes[0].className : "";
      return (endpoint.httpMethod + " " + endpoint.path + " " + entry).toLowerCase().includes(q);
    });

  return (
    <div className="endpoint-list">
      <div className="section-title">
        <span>Endpoints</span>
        <span>{q ? `${visible.length} of ${endpoints.length}` : endpoints.length}</span>
      </div>
      <input
        className="search"
        type="search"
        placeholder="Search path, method or controller"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        aria-label="Search endpoints"
      />
      {visible.length === 0 && <div className="hint">No endpoint matches "{query}".</div>}
      {visible.map(({ endpoint, index }) => (
        <button
          key={endpoint.httpMethod + " " + endpoint.path + " " + index}
          type="button"
          className={"endpoint-item" + (index === selectedIndex ? " active" : "")}
          onClick={() => onSelect(index)}
        >
          <span className="badge" data-m={endpoint.httpMethod}>
            {endpoint.httpMethod}
          </span>
          <span className="endpoint-path">{endpoint.path}</span>
        </button>
      ))}
    </div>
  );
}
