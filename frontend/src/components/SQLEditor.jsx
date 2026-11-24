
import { useState } from "react";
import Editor from "@monaco-editor/react";
import "./SQLEditor.css";

function SQLEditor() {
  const [sql, setSql] = useState(
    "CREATE TABLE students (\n" +
    "    id INT PRIMARY KEY,\n" +
    "    name VARCHAR(100),\n" +
    "    email VARCHAR(100)\n" +
    ");"
  );

  const [result, setResult] = useState(
    "Run a query to see the output here."
  );

  const [status, setStatus] = useState("Ready");

  const runQuery = () => {
    if (!sql.trim()) {
      setResult("Please enter an SQL query.");
      setStatus("Error");
      return;
    }

    setStatus("Demo");
    setResult(
      "SQL query received successfully!\n\n" +
      "Backend is not connected yet.\n" +
      "Actual SQL execution will be available after backend integration."
    );
  };

  const clearEditor = () => {
    setSql("");
    setResult("Run a query to see the output here.");
    setStatus("Ready");
  };

  return (
    <div className="sql-container">

      <div className="sql-header">
        <h1>ShadowBase SQL Editor</h1>
        <p>Write, edit and execute SQL queries</p>
      </div>

      <div className="sql-workspace">

        <div className="sql-toolbar">
          <div className="toolbar-left">

            <button className="run-btn" onClick={runQuery}>
              <span>▶</span> Run Query
            </button>

            <button className="clear-btn" onClick={clearEditor}>
              <span>⌫</span> Clear Editor
            </button>

          </div>

          <div className={`sql-status ${status.toLowerCase()}`}>
            <span className="status-dot"></span>
            {status}
          </div>
        </div>

        <div className="sql-editor-box">
          <Editor
            height="460px"
            language="sql"
            theme="vs-dark"
            value={sql}
            onChange={(value) => setSql(value || "")}
            options={{
              minimap: { enabled: false },
              fontSize: 15,
              lineNumbers: "on",
              automaticLayout: true,
              wordWrap: "on",
              scrollBeyondLastLine: false,
              padding: { top: 15 },
              tabSize: 4
            }}
          />
        </div>

        <div className="results-section">
          <div className="results-header">
            <h2>Query Output</h2>
            <span className={`output-status ${status.toLowerCase()}`}>
              {status}
            </span>
          </div>

          <pre>{result}</pre>
        </div>

      </div>
    </div>
  );
}

export default SQLEditor;
