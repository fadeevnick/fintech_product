import React from "react";
import { createRoot } from "react-dom/client";
import { surfaceLabels } from "@minifin/ui";
import "./styles.css";

function App() {
  return (
    <main className="shell shell-enduser">
      <section className="topbar">
        <span>MiniFin</span>
        <strong>{surfaceLabels.enduser}</strong>
      </section>
      <section className="content auth-prototype" aria-label="End-user identity prototype">
        <div className="prototype-header">
          <h1>Login/register prototype</h1>
          <p>Phase 02 identity checkpoint for registration, email verification, login and session states.</p>
        </div>

        <section className="prototype-grid">
          <article className="state-panel">
            <span className="state-kicker">Register</span>
            <h2>Create end-user</h2>
            <label>
              Email
              <input value="ava@example.test" readOnly />
            </label>
            <label>
              Password
              <input value="correct horse battery" readOnly type="password" />
            </label>
            <button type="button">Submit registration</button>
          </article>

          <article className="state-panel">
            <span className="state-kicker">Verify</span>
            <h2>Email pending</h2>
            <p className="state-copy">Local verification token is returned only for smoke automation.</p>
            <div className="status-line status-waiting">Waiting for verification token</div>
            <div className="status-line status-success">Verified session can continue to login</div>
          </article>

          <article className="state-panel">
            <span className="state-kicker">Login</span>
            <h2>Start session</h2>
            <label>
              Email
              <input value="ava@example.test" readOnly />
            </label>
            <label>
              Password
              <input value="correct horse battery" readOnly type="password" />
            </label>
            <button type="button">Create session</button>
          </article>

          <article className="state-panel">
            <span className="state-kicker">Session</span>
            <h2>Current user</h2>
            <div className="session-box">
              <strong>Authenticated</strong>
              <span>GET /api/v1/enduser/me returns profile data.</span>
            </div>
            <div className="session-box denied">
              <strong>Unauthenticated</strong>
              <span>Protected endpoint returns 401 with common error shape.</span>
            </div>
          </article>
        </section>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
