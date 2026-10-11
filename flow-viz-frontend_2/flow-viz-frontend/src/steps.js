/**
 * Turns one endpoint graph into an ordered list of steps (execution order, top to bottom).
 *
 * Each step is a method of your code. `via` is the call that first reaches it (who calls it, at which
 * line); `otherCalls` are any further places that call it too (for example a recursive call).
 * `depth` is how deep it sits in the call chain, used for indentation.
 */
export function buildSteps(graph) {
  if (!graph || graph.nodes.length === 0) return [];

  const byId = new Map(graph.nodes.map((n) => [n.id, n]));
  const outgoing = new Map();
  const incoming = new Map();
  for (const edge of graph.edges) {
    if (!outgoing.has(edge.from)) outgoing.set(edge.from, []);
    outgoing.get(edge.from).push(edge);
    if (!incoming.has(edge.to)) incoming.set(edge.to, []);
    incoming.get(edge.to).push(edge);
  }

  const steps = [];
  const seen = new Set();

  const visit = (id, depth, via) => {
    if (seen.has(id)) return;
    seen.add(id);
    steps.push({ node: byId.get(id), depth, via });
    for (const edge of outgoing.get(id) || []) visit(edge.to, depth + 1, edge);
  };
  visit(graph.nodes[0].id, 0, null); // the backend always lists the endpoint method first

  return steps.map((step) => ({
    ...step,
    caller: step.via ? byId.get(step.via.from) : null,
    otherCalls: (incoming.get(step.node.id) || [])
      .filter((edge) => edge !== step.via)
      .map((edge) => ({ edge, caller: byId.get(edge.from) })),
  }));
}
