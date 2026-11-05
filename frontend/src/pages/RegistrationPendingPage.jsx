import { Link } from "react-router-dom";

export default function RegistrationPendingPage() {
  return (
    <main className="login-page">
      <section className="card access-denied">
        <div className="eyebrow">Recruiter registration</div>
        <h1>Your recruiter account is awaiting Admin approval.</h1>
        <p>You can sign in after an administrator approves your registration.</p>
        <Link className="btn btn-primary" to="/login.html">Return to sign in</Link>
      </section>
    </main>
  );
}
