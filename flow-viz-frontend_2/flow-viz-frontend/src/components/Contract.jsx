/** What goes in and what comes out, read from the controller method signature. */
export default function Contract({ graph }) {
  const inputs = graph.inputs || [];

  return (
    <section className="block" aria-label="Request and response">
      <h3 className="block-title">Request</h3>
      {inputs.length === 0 ? (
        <p className="muted">This endpoint takes no input.</p>
      ) : (
        <table className="inputs">
          <tbody>
            {inputs.map((input, i) => (
              <tr key={i}>
                <td>
                  <span className="kind">{input.kind}</span>
                </td>
                <td className="mono">{input.name}</td>
                <td className="mono type">{input.type}</td>
                <td className="req">{input.required ? "required" : "optional"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {graph.returns && (
        <div className="returns">
          <span className="block-title">Returns</span>
          <code>{graph.returns}</code>
        </div>
      )}
    </section>
  );
}
