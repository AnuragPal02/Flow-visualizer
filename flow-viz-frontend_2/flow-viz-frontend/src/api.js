const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

/** POST /analyze. sourcePath is a folder on the machine running the backend. */
export async function analyzeRepo(sourcePath) {
  let res;
  try {
    res = await fetch(`${API_BASE}/analyze`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sourcePath }),
    });
  } catch {
    throw new Error("Cannot reach the backend. Is it running on " + API_BASE + "?");
  }

  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* not JSON, keep status text */
    }
    throw new Error(message);
  }
  return res.json();
}
