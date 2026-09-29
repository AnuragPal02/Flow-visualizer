import { Handle, Position } from 'reactflow';

export const TYPE_COLORS = {
  CONTROLLER: '#2563eb',
  SERVICE: '#059669',
  REPOSITORY: '#d97706',
  EXTERNAL_CALL: '#dc2626',
  COMPONENT: '#7c3aed',
  LIBRARY: '#94a3b8',
  UNKNOWN: '#64748b',
};

export default function CustomNode({ data }) {
  const color = TYPE_COLORS[data.type] || TYPE_COLORS.UNKNOWN;
  return (
    <div className="flow-node" style={{ borderColor: color }}>
      <Handle type="target" position={Position.Top} />
      <div className="flow-node-type" style={{ background: color }}>
        {data.type}
      </div>
      <div className="flow-node-class">{data.className}</div>
      <div className="flow-node-method">{data.methodName}()</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
