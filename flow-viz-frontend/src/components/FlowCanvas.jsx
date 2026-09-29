import { useMemo, useState, useCallback } from 'react';
import ReactFlow, { Background, Controls, MiniMap } from 'reactflow';
import CustomNode, { TYPE_COLORS } from './CustomNode.jsx';
import NodeDetailsPanel from './NodeDetailsPanel.jsx';
import { layoutGraph } from '../layout/dagreLayout.js';

const nodeTypes = { custom: CustomNode };

export default function FlowCanvas({ graph }) {
  const [selectedNode, setSelectedNode] = useState(null);

  const { nodes, edges } = useMemo(() => {
    if (!graph) return { nodes: [], edges: [] };

    const rfNodes = graph.nodes.map((n) => ({
      id: n.id,
      type: 'custom',
      data: n,
      position: { x: 0, y: 0 }, // overwritten by layoutGraph below
    }));

    const rfEdges = graph.edges.map((e, i) => ({
      id: `e-${i}-${e.from}-${e.to}`,
      source: e.from,
      target: e.to,
    }));

    return layoutGraph(rfNodes, rfEdges);
  }, [graph]);

  const handleNodeClick = useCallback((_event, node) => {
    setSelectedNode(node);
  }, []);

  if (!graph) {
    return <div className="canvas-empty">Analyze a repo, then pick an endpoint to see its flow.</div>;
  }

  return (
    <div className="canvas-wrapper">
      <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} onNodeClick={handleNodeClick} fitView>
        <Background />
        <Controls />
        <MiniMap nodeColor={(n) => TYPE_COLORS[n.data?.type] || TYPE_COLORS.UNKNOWN} />
      </ReactFlow>
      <NodeDetailsPanel node={selectedNode} onClose={() => setSelectedNode(null)} />
    </div>
  );
}
