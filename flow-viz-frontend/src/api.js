const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

/**
 * Calls the backend's POST /analyze. sourcePath must be an absolute path to
 * the TARGET repo's src/main/java on the machine running the backend —
 * not this frontend. classpath is optional but strongly recommended (see
 * backend README) — without it, library/framework calls won't classify
 * correctly.
 */
export async function analyzeRepo(sourcePath, classpath) {
  const res = await fetch(`${API_BASE}/analyze`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sourcePath, classpath }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Analyze failed (${res.status}): ${text || res.statusText}`);
  }

  return res.json();
}
