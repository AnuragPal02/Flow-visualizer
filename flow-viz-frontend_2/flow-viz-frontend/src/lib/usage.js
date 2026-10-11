/** For every step: which endpoints reach it. Lets a step answer "what else uses this?". */
export function buildUsage(endpoints) {
  const usage = new Map();
  endpoints.forEach((endpoint, index) => {
    const label = endpoint.httpMethod + " " + endpoint.path;
    for (const node of endpoint.nodes) {
      if (!usage.has(node.id)) usage.set(node.id, []);
      usage.get(node.id).push({ index, label });
    }
  });
  return usage;
}

export function otherEndpoints(usage, nodeId, currentIndex) {
  return (usage.get(nodeId) || []).filter((u) => u.index !== currentIndex);
}
