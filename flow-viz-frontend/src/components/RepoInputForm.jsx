import { useState } from 'react';

export default function RepoInputForm({ onAnalyze, loading, error }) {
  const [sourcePath, setSourcePath] = useState('');
  const [classpath, setClasspath] = useState('');

  function handleSubmit(e) {
    e.preventDefault();
    if (!sourcePath.trim()) return;
    onAnalyze(sourcePath.trim(), classpath.trim());
  }

  return (
    <form className="repo-form" onSubmit={handleSubmit}>
      <label>
        Repo src/main/java path
        <input
          type="text"
          placeholder="/path/to/target-repo/src/main/java"
          value={sourcePath}
          onChange={(e) => setSourcePath(e.target.value)}
        />
      </label>
      <label>
        Classpath (optional — from `mvn dependency:build-classpath`)
        <textarea
          placeholder="/root/.m2/repository/org/springframework/..."
          rows={3}
          value={classpath}
          onChange={(e) => setClasspath(e.target.value)}
        />
      </label>
      <button type="submit" disabled={loading}>
        {loading ? 'Analyzing…' : 'Analyze Repo'}
      </button>
      {error && <div className="form-error">{error}</div>}
    </form>
  );
}
