import { buildSteps } from "../steps.js";
import { typeLabel, kindLabel } from "./labels.js";

const quote = (s) => String(s).replace(/"/g, "#quot;");

/** Mermaid flowchart of the endpoint, ready to paste into a README or PR. */
export function toMermaid(graph) {
  const ids = new Map(graph.nodes.map((n, i) => [n.id, "n" + i]));
  const out = ["flowchart TD"];
  for (const n of graph.nodes) {
    out.push(`  ${ids.get(n.id)}["${quote(n.className + "." + n.methodName + "()")}"]`);
  }
  for (const e of graph.edges) {
    const label = e.line > 0 ? `|"line ${e.line}"|` : "";
    out.push(`  ${ids.get(e.from)} -->${label} ${ids.get(e.to)}`);
  }
  return out.join("\n");
}

/** Markdown description of the endpoint: contract, at-a-glance facts and the step-by-step flow. */
export function toMarkdown(graph) {
  const steps = buildSteps(graph);
  const s = graph.summary;
  const out = [`## ${graph.httpMethod} ${graph.path}`, ""];

  if (graph.returns) out.push(`Returns \`${graph.returns}\``, "");
  if (graph.inputs && graph.inputs.length > 0) {
    out.push("| Input | Name | Type | Required |", "| --- | --- | --- | --- |");
    for (const i of graph.inputs) out.push(`| ${i.kind} | ${i.name} | \`${i.type}\` | ${i.required ? "yes" : "no"} |`);
    out.push("");
  }

  if (s) {
    out.push("**At a glance**", "");
    out.push(`- Steps: ${s.steps} (${s.layers.map(typeLabel).join(" -> ")})`);
    if (s.reads.length) out.push(`- Reads: ${s.reads.join(", ")}`);
    if (s.writes.length) out.push(`- Writes: ${s.writes.join(", ")}`);
    if (s.touches.length) out.push(`- Touches: ${s.touches.join(", ")}`);
    if (s.integrations.length) {
      out.push(`- Calls outside: ${s.integrations.map((i) => `${kindLabel(i.kind)} ${i.client} x${i.calls}`).join(", ")}`);
    }
    out.push(`- Transactional: ${s.transactional ? "yes" : "no"}`);
    if (s.security.length) out.push(`- Security: ${s.security.join("; ")}`);
    out.push("");
  }

  out.push("**Flow**", "");
  steps.forEach((step, i) => {
    const n = step.node;
    out.push(`${i + 1}. \`${n.className}.${n.methodName}()\` (${typeLabel(n.type)})${n.doc ? " - " + n.doc : ""}`);
    if (step.via && step.caller) {
      const where = step.via.line ? ` at line ${step.via.line}` : "";
      const code = step.via.code ? `: \`${step.via.code}\`` : "";
      out.push(`   - called by \`${step.caller.className}.${step.caller.methodName}()\`${where}${code}`);
    }
    out.push(`   - \`${n.file}\``);
  });
  return out.join("\n");
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    const box = document.createElement("textarea");
    box.value = text;
    box.style.position = "fixed";
    box.style.opacity = "0";
    document.body.appendChild(box);
    box.select();
    let ok = false;
    try {
      ok = document.execCommand("copy");
    } catch {
      ok = false;
    }
    box.remove();
    return ok;
  }
}
