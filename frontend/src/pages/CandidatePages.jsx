import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../services/api.js";
import { Badge, Button, Card, DataTable, Field, PageHeader, Pagination, useToast } from "../components/Shared.jsx";
import { ToastRegion } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";

const PAGE_SIZE = 10;

export function CandidatesPage() {
  const { canManage } = useAuth();
  const [filters, setFilters] = useState({ search: "", passingYear: "", status: "" });
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const toast = useToast();
  const navigate = useNavigate();

  async function load() {
    setLoading(true);
    setError("");
    try {
      setData(await api.get("/candidates", { ...filters, page, size: PAGE_SIZE }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, [page, filters.search, filters.passingYear, filters.status]);

  async function remove(candidate) {
    if (!window.confirm(`Delete ${candidate.fullName}?`)) return;
    try {
      await api.delete(`/candidates/${candidate.candidateId}`);
      toast.push("Candidate deleted.");
      load();
    } catch (requestError) { toast.push(requestError.message, true); }
  }

  return <>
    <PageHeader eyebrow="Talent directory" title="Candidates" subtitle="Find and manage candidate profiles."
      action={canManage && <Button onClick={() => navigate("/candidate-form.html")}>＋ Add candidate</Button>} />
    <Card>
      <div className="toolbar">
        <input aria-label="Search candidates" className="search-field" placeholder="Search name, email or skills…" value={filters.search} onChange={(event) => { setPage(0); setFilters({ ...filters, search: event.target.value }); }} />
        <input aria-label="Filter by passing year" type="number" placeholder="Passing year" value={filters.passingYear} onChange={(event) => { setPage(0); setFilters({ ...filters, passingYear: event.target.value }); }} />
        <select aria-label="Filter by status" value={filters.status} onChange={(event) => { setPage(0); setFilters({ ...filters, status: event.target.value }); }}>
          <option value="">All statuses</option><option>ACTIVE</option><option>INACTIVE</option>
        </select>
      </div>
      <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "fullName", label: "Candidate", render: (row) => <div className="primary-cell"><strong>{row.fullName}</strong><span>{row.email}</span></div> },
        { key: "qualification", label: "Qualification" },
        { key: "passingYear", label: "Passing year" },
        { key: "percentage", label: "Score", render: (row) => `${row.percentage}%` },
        { key: "skills", label: "Skills", render: (row) => row.skills || "—" },
        { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
        { key: "actions", label: "Actions", render: (row) => <div className="table-actions">
          <Link className="btn btn-secondary btn-small" to={`/candidate-details.html?id=${row.candidateId}`}>View</Link>
          {canManage && <Link className="btn btn-secondary btn-small" to={`/candidate-form.html?id=${row.candidateId}`}>Edit</Link>}
          {canManage && <Button variant="danger" small onClick={() => remove(row)}>Delete</Button>}
        </div> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={PAGE_SIZE} onPageChange={setPage} />
    </Card>
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

export function CandidateFormPage() {
  const { canManage } = useAuth();
  const [query] = useSearchParams();
  const id = query.get("id");
  const navigate = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [candidate, setCandidate] = useState(null);

  useEffect(() => {
    if (!id) return;
    api.get(`/candidates/${id}`).then(setCandidate).catch((error) => setLoadError(error.message));
  }, [id]);

  if (!canManage) return <PageHeader title="Access restricted" subtitle="Your role can view candidate profiles but cannot change them." />;
  if (loadError) return <div className="error-state card">{loadError}</div>;
  if (id && !candidate) return <div className="loading-state">Loading candidate…</div>;

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const request = {
      fullName: form.get("fullName").trim(),
      email: form.get("email").trim(),
      mobile: form.get("mobile").trim(),
      qualification: form.get("qualification").trim(),
      passingYear: Number(form.get("passingYear")),
      percentage: Number(form.get("percentage")),
      backlogCount: Number(form.get("backlogCount")),
      skills: form.get("skills").trim() || null,
    };
    try {
      const saved = id ? await api.put(`/candidates/${id}`, request) : await api.post("/candidates", request);
      toast.push("Candidate saved.");
      navigate(`/candidate-details.html?id=${saved.candidateId}`, { replace: true });
    } catch (error) { toast.push(error.message, true); }
    finally { setBusy(false); }
  }

  return <>
    <PageHeader eyebrow="Talent directory" title={id ? "Edit candidate" : "Add a candidate"} subtitle="Capture the profile details needed by your recruiting team." />
    <Card title="Candidate profile">
      <form className="card-body" onSubmit={submit}>
        <div className="form-grid">
          <Field label="Full name" name="fullName" defaultValue={candidate?.fullName} required maxLength={160} />
          <Field label="Email address" name="email" type="email" defaultValue={candidate?.email} required maxLength={254} />
          <Field label="Phone number" name="mobile" type="tel" defaultValue={candidate?.mobile} required maxLength={30} />
          <Field label="Qualification" name="qualification" defaultValue={candidate?.qualification} required maxLength={160} />
          <Field label="Passing year" name="passingYear" type="number" min="1901" max="2155" defaultValue={candidate?.passingYear} required />
          <Field label="Percentage / score" name="percentage" type="number" min="0" max="100" step="0.01" defaultValue={candidate?.percentage} required />
          <Field label="Backlogs" name="backlogCount" type="number" min="0" step="1" defaultValue={candidate?.backlogCount ?? 0} required />
          <Field label="Skills" name="skills" as="textarea" className="span-all" defaultValue={candidate?.skills} placeholder="Java, SQL, Spring Boot" maxLength={5000} />
        </div>
        <div className="form-actions"><Link className="btn btn-secondary" to="/candidates.html">Cancel</Link><Button type="submit" disabled={busy}>{busy ? "Saving…" : "Save candidate"}</Button></div>
      </form>
    </Card>
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

export function CandidateDetailsPage() {
  const [query] = useSearchParams();
  const id = query.get("id");
  const [candidate, setCandidate] = useState(null);
  const [applications, setApplications] = useState([]);
  const [error, setError] = useState("");
  const { canManage } = useAuth();
  useEffect(() => {
    if (!id) { setError("A candidate ID is required."); return; }
    api.get(`/candidates/${id}`)
      .then(async (profile) => {
        setCandidate(profile);
        const appPage = await api.get("/applications", { candidate: profile.fullName, size: 100 });
        const content = appPage.content || [];
        setApplications(content.filter((application) => application.candidateId === Number(id)));
      })
      .catch((requestError) => setError(requestError.message));
  }, [id]);

  if (error) return <div className="error-state card">{error}</div>;
  if (!candidate) return <div className="loading-state">Loading candidate profile…</div>;
  return <>
    <PageHeader eyebrow="Talent directory" title="Candidate profile" subtitle="Profile information and recruitment history."
      action={canManage && <Link className="btn btn-primary" to={`/candidate-form.html?id=${candidate.candidateId}`}>Edit profile</Link>} />
    <div className="profile-grid">
      <Card className="profile-card">
        <div className="profile-avatar">{candidate.fullName.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase()}</div>
        <div className="profile-name">{candidate.fullName}</div>
        <p className="profile-contact">{candidate.email}<br />{candidate.mobile}</p>
        <Badge value={candidate.status} />
        <div className="detail-list">
          <div className="detail-item"><span>Qualification</span><strong>{candidate.qualification}</strong></div>
          <div className="detail-item"><span>Passing year</span><strong>{candidate.passingYear}</strong></div>
          <div className="detail-item"><span>Percentage</span><strong>{candidate.percentage}%</strong></div>
          <div className="detail-item"><span>Backlogs</span><strong>{candidate.backlogCount}</strong></div>
          <div className="detail-item"><span>Skills</span><strong>{candidate.skills || "—"}</strong></div>
        </div>
      </Card>
      <Card title="Application history">
        <DataTable rows={applications} error={applications.length ? "" : undefined} columns={[
          { key: "jobTitle", label: "Role", render: (row) => <div className="primary-cell"><strong>{row.jobTitle}</strong><span>{row.companyName}</span></div> },
          { key: "appliedAt", label: "Applied", render: (row) => new Date(row.appliedAt).toLocaleDateString() },
          { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
          { key: "applicationId", label: "History", render: () => <Link className="btn btn-secondary btn-small" to={`/applications.html?candidate=${encodeURIComponent(candidate.fullName)}`}>View applications</Link> },
        ]} empty="No applications are linked to this candidate yet." />
      </Card>
    </div>
  </>;
}
