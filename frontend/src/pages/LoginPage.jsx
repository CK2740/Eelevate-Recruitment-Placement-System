import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { Button, Field } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";

export default function LoginPage() {
  const { user, loading, signIn } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (!loading && user) return <Navigate to="/dashboard.html" replace />;

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const form = new FormData(event.currentTarget);
    try {
      await signIn(form.get("email"), form.get("password"));
      navigate("/dashboard.html", { replace: true });
    } catch (requestError) {
      setError(requestError.message || "Sign-in failed. Check your credentials and try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-layout">
        <section className="login-aside">
          <a className="brand" href="#/login.html"><span className="brand-mark">E</span><span>elevate</span></a>
          <div>
            <div className="eyebrow">Recruitment, in focus</div>
            <h1>Find the people who move you forward.</h1>
            <p>One calm workspace to connect candidates, companies and every step in between.</p>
            <div className="login-decoration" aria-hidden="true" />
          </div>
          <span className="small-note">A clearer path from application to placement.</span>
        </section>
        <section className="login-form-wrap">
          <form className="login-form" onSubmit={submit}>
            <div className="eyebrow">Welcome back</div>
            <h2>Sign in to your workspace</h2>
            <p>Use your organization account to continue.</p>
            <Field label="Work email" name="email" type="email" placeholder="you@company.com" autoComplete="username" required />
            <Field label="Password" name="password" type="password" placeholder="Enter your password" autoComplete="current-password" required />
            <div className="login-error" role="alert">{error}</div>
            <Button type="submit" disabled={busy}>{busy ? "Signing in…" : "Sign in"} <span aria-hidden="true">→</span></Button>
            <div className="demo-note">Access is provided by your administrator. Your role and available workspace areas are loaded securely from the backend after sign-in.</div>
          </form>
        </section>
      </div>
    </main>
  );
}
