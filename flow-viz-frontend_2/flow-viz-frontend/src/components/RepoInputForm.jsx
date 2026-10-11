import { useState } from "react";

export default function RepoInputForm({ onAnalyze, loading, error }) {
  const [sourcePath, setSourcePath] = useState("");

  function handleSubmit(e) {
    e.preventDefault();
    if (sourcePath.trim()) onAnalyze(sourcePath.trim());
  }

  return (
    <form className="repo-form" onSubmit={handleSubmit}>
      <label htmlFor="repo-path">Target Java source folder</label>
      <input
        id="repo-path"
        type="text"
        placeholder={"C:\\project\\src\\main\\java"}
        value={sourcePath}
        onChange={(e) => setSourcePath(e.target.value)}
        spellCheck={false}
        autoComplete="off"
      />
      <div className="hint">The src/main/java folder, or the repo folder that contains it.</div>
      <button type="submit" disabled={loading || !sourcePath.trim()}>
        {loading ? "Analyzing..." : "Analyze Repo"}
      </button>
      {error && <div className="form-error">{error}</div>}
    </form>
  );
}
