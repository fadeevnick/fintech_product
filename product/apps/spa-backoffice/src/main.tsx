import React from "react";
import { createRoot } from "react-dom/client";
import { surfaceLabels } from "@minifin/ui";
import "./styles.css";

function App() {
  return (
    <main className="shell shell-backoffice">
      <section className="topbar">
        <span>MiniFin</span>
        <strong>{surfaceLabels.backoffice}</strong>
      </section>
      <section className="content">
        <h1>Backoffice shell</h1>
        <p>Phase 01 deployment shell. Case review workflows start in later approved slices.</p>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
