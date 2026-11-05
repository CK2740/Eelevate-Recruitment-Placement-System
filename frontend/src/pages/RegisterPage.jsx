import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { Button, Field } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";

export default function RegisterPage() {
  const { user, loading, signUp } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [accountType, setAccountType] = useState("CANDIDATE");

  if (!loading && user) return <Navigate to="/dashboard.html" replace />;

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const fullName = form.get("fullName").trim();
    const email = form.get("email").trim();
    const password = form.get("password");
    const confirmPassword = form.get("confirmPassword");
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      setBusy(false);
      return;
    }

    try {
      const result = await signUp(fullName, email, password, confirmPassword, accountType);
      if (accountType === "RECRUITER") {
        navigate("/registration-pending.html", { replace: true, state: { message: result.message } });
      } else {
        navigate("/dashboard.html", { replace: true });
      }
    } catch (requestError) {
      setError(requestError.message || "Account creation failed. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-layout">
        <section className="login-aside">
          <Link className="brand" to="/login.html"><span className="brand-mark">E</span><span>elevate</span></Link>
          <div>
            <div className="eyebrow">Start recruiting</div>
            <h1>Your next opportunity starts here.</h1>
            <p>Create a workspace account to connect with candidates, companies and opportunities.</p>
            <div className="login-decoration" aria-hidden="true" />
          </div>
          <span className="small-note">Secure access, with your role managed by the system.</span>
        </section>
        <section className="login-form-wrap">
          <form className="login-form" onSubmit={submit}>
            <div className="eyebrow">Get started</div>
            <h2>Create your account</h2>
            <p>Create a candidate portal or request a recruiter account.</p>
            <Field label="Account type" name="accountType" as="select" value={accountType} onChange={(event) => setAccountType(event.target.value)}>
                <option value="CANDIDATE">Candidate</option>
                <option value="RECRUITER">Recruiter</option>
            </Field>
            <Field label="Full name" name="fullName" type="text" placeholder="Your full name" autoComplete="name" maxLength={160} required />
            <Field label="Email" name="email" type="email" placeholder="you@company.com" autoComplete="email" maxLength={254} required />
            <Field label="Password" name="password" type="password" placeholder="At least 12 characters" autoComplete="new-password" minLength={12} maxLength={72} required />
            <Field label="Confirm password" name="confirmPassword" type="password" placeholder="Enter your password again" autoComplete="new-password" minLength={12} maxLength={72} required />
            <div className="login-error" role="alert">{error}</div>
            <Button type="submit" disabled={busy}>{busy ? "Creating account…" : "Create account"} <span aria-hidden="true">→</span></Button>
            <div className="auth-switch">Already have an account? <Link to="/login.html">Sign in</Link></div>
            <div className="demo-note">
              Candidate accounts are active immediately. Recruiter accounts require Admin approval before sign-in.
            </div>
          </form>
        </section>
      </div>
    </main>
  );
}
