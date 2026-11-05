import { useEffect, useState } from "react";
import { api, apiRequest } from "../services/api.js";
import {
  Badge,
  Button,
  Card,
  DataTable,
  Field,
  Modal,
  PageHeader,
  Pagination,
  ToastRegion,
  useToast,
} from "../components/Shared.jsx";

const PAGE_SIZE = 10;

function usePortalPage(path) {
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      setData(await api.get(path, { page, size: PAGE_SIZE }));
    } catch (requestError) {
      setError(requestError.message || "Could not load your information.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [path, page]);
  return { data, page, setPage, loading, error, load };
}

export function CandidateProfilePage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  async function loadProfile() {
    setLoading(true);
    setError("");
    try {
      setProfile(await api.get("/portal/profile"));
    } catch (requestError) {
      setError(requestError.message || "Could not load your profile.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadProfile(); }, []);

  async function saveProfile(event) {
    event.preventDefault();
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const request = {
      fullName: form.get("fullName").trim(),
      mobile: form.get("mobile").trim(),
      qualification: form.get("qualification").trim(),
      passingYear: Number(form.get("passingYear")),
      percentage: Number(form.get("percentage")),
      backlogCount: Number(form.get("backlogCount")),
      skills: form.get("skills").trim() || null,
      experience: form.get("experience").trim() || null,
    };
    try {
      setProfile(await api.put("/portal/profile", request));
      toast.push("Your profile was saved.");
    } catch (requestError) {
      toast.push(requestError.message || "Could not save your profile.", true);
    } finally {
      setBusy(false);
    }
  }

  async function uploadResume(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const file = new FormData(form).get("resume");
    if (!file || file.size === 0) return;
    setBusy(true);
    try {
      await apiRequest("/portal/resume", { method: "POST", body: new FormData(form) });
      toast.push("Resume uploaded.");
      await loadProfile();
      form.reset();
    } catch (requestError) {
      toast.push(requestError.message || "Could not upload your resume.", true);
    } finally {
      setBusy(false);
    }
  }

  async function downloadResume() {
    try {
      const file = await apiRequest("/portal/resume", { responseType: "blob" });
      const url = URL.createObjectURL(file);
      const link = document.createElement("a");
      link.href = url;
      link.download = profile.resumeName || "resume";
      link.click();
      URL.revokeObjectURL(url);
    } catch (requestError) {
      toast.push(requestError.message || "Could not download your resume.", true);
    }
  }

  if (loading) return <div className="loading-state">Loading your profile…</div>;
  if (error) return <div className="error-state card">{error}</div>;

  return <>
    <PageHeader eyebrow="Candidate portal" title="My profile"
      subtitle="Manage your contact details, education, skills, experience and resume." />
    <Card title="Personal and education details">
      <form className="card-body" onSubmit={saveProfile}>
        <div className="form-grid">
          <Field label="Full name" name="fullName" defaultValue={profile.fullName} required maxLength={160} />
          <Field label="Email" name="emailDisplay" type="email" value={profile.email} readOnly />
          <Field label="Phone number" name="mobile" type="tel" defaultValue={profile.mobile || ""} required maxLength={30} />
          <Field label="Qualification" name="qualification" defaultValue={profile.qualification || ""} required maxLength={160} />
          <Field label="Passing year" name="passingYear" type="number" min="1901" max="2155" defaultValue={profile.passingYear || ""} required />
          <Field label="Percentage / score" name="percentage" type="number" min="0" max="100" step="0.01" defaultValue={profile.percentage ?? ""} required />
          <Field label="Backlogs" name="backlogCount" type="number" min="0" defaultValue={profile.backlogCount ?? 0} required />
          <Field label="Skills" name="skills" as="textarea" defaultValue={profile.skills || ""} maxLength={5000} placeholder="Java, SQL, Spring Boot" />
          <Field label="Experience" name="experience" as="textarea" className="span-all" defaultValue={profile.experience || ""} maxLength={10000} placeholder="Relevant experience, projects or internships" />
        </div>
        <div className="form-actions"><Button type="submit" disabled={busy}>{busy ? "Saving…" : "Save profile"}</Button></div>
      </form>
    </Card>
    <Card title="Resume" className="section-gap">
      <div className="card-body">
        <p>{profile.resumeAvailable ? `Current file: ${profile.resumeName}` : "No resume uploaded yet."}</p>
        <div className="form-actions">
          {profile.resumeAvailable && <Button variant="secondary" onClick={downloadResume}>View / download resume</Button>}
          <form className="form-actions" onSubmit={uploadResume}>
            <input aria-label="Resume file" type="file" name="resume" accept=".pdf,.doc,.docx" required />
            <Button type="submit" disabled={busy}>{busy ? "Uploading…" : profile.resumeAvailable ? "Replace resume" : "Upload resume"}</Button>
          </form>
        </div>
        <p className="small-note">PDF, DOC or DOCX files up to 10 MB are accepted.</p>
      </div>
    </Card>
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

export function CandidatePortalJobsPage() {
  const [filters, setFilters] = useState({ search: "", company: "", technology: "", location: "", passingYear: "" });
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [applying, setApplying] = useState(false);
  const toast = useToast();

  async function load() {
    setLoading(true);
    setError("");
    try {
      setData(await api.get("/portal/jobs", { ...filters, page, size: PAGE_SIZE }));
    } catch (requestError) {
      setError(requestError.message || "Could not load available jobs.");
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, [page, filters.search, filters.company, filters.technology, filters.location, filters.passingYear]);

  async function apply(job) {
    setApplying(true);
    try {
      await api.post(`/portal/jobs/${job.jobId}/applications`);
      toast.push("Your application was submitted.");
      setSelected(null);
    } catch (requestError) {
      toast.push(requestError.message || "Could not submit your application.", true);
    } finally {
      setApplying(false);
    }
  }

  function updateFilter(key, value) {
    setPage(0);
    setFilters((current) => ({ ...current, [key]: value }));
  }

  return <>
    <PageHeader eyebrow="Candidate portal" title="Browse jobs" subtitle="Explore open opportunities and review eligibility before applying." />
    <Card>
      <div className="toolbar">
        <input aria-label="Search jobs" className="search-field" placeholder="Search title, company or technology" value={filters.search} onChange={(event) => updateFilter("search", event.target.value)} />
        <input aria-label="Filter by company" placeholder="Company" value={filters.company} onChange={(event) => updateFilter("company", event.target.value)} />
        <input aria-label="Filter by technology" placeholder="Technology" value={filters.technology} onChange={(event) => updateFilter("technology", event.target.value)} />
        <input aria-label="Filter by location" placeholder="Location" value={filters.location} onChange={(event) => updateFilter("location", event.target.value)} />
        <input aria-label="Filter by passing year" type="number" placeholder="Passing year" value={filters.passingYear} onChange={(event) => updateFilter("passingYear", event.target.value)} />
      </div>
      <DataTable loading={loading} error={error} rows={data.content || []} empty="No currently open jobs match these filters." columns={[
        { key: "jobTitle", label: "Position", render: (job) => <div className="primary-cell"><strong>{job.jobTitle}</strong><span>{job.companyName}</span></div> },
        { key: "technology", label: "Technology" },
        { key: "location", label: "Location" },
        { key: "packageAmount", label: "Package", render: (job) => job.packageAmount == null ? "—" : job.packageAmount },
        { key: "applicationDeadline", label: "Deadline" },
        { key: "actions", label: "Actions", render: (job) => <Button variant="secondary" small onClick={() => setSelected(job)}>View details</Button> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={PAGE_SIZE} onPageChange={setPage} />
    </Card>
    {selected && <Modal title={selected.jobTitle} onClose={() => setSelected(null)}>
      <div className="card-body">
        <p><strong>{selected.companyName}</strong> · {selected.location} · {selected.technology}</p>
        <p>{selected.jobDescription || "No additional description provided."}</p>
        <p><strong>Required skills:</strong> {selected.skills || "Not specified"}</p>
        <p><strong>Qualification:</strong> {selected.requiredQualification || "Not specified"}</p>
        <p><strong>Eligibility:</strong> Minimum score {selected.eligibilityCriteria?.minimumPercentage ?? 0}%;
          passing year {selected.eligibilityCriteria?.minimumPassingYear ?? "any"}–{selected.eligibilityCriteria?.maximumPassingYear ?? "any"};
          maximum backlogs {selected.eligibilityCriteria?.maximumBacklogs ?? 0}.</p>
        <p><strong>Apply by:</strong> {selected.applicationDeadline}</p>
        <div className="form-actions">
          <Button variant="secondary" onClick={() => setSelected(null)}>Close</Button>
          <Button disabled={applying} onClick={() => apply(selected)}>{applying ? "Applying…" : "Apply for this job"}</Button>
        </div>
      </div>
    </Modal>}
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

export function CandidateApplicationsPage() {
  const { data, page, setPage, loading, error } = usePortalPage("/portal/applications");
  const [history, setHistory] = useState(null);
  const [historyError, setHistoryError] = useState("");

  async function showHistory(application) {
    setHistoryError("");
    try {
      setHistory(await api.get(`/portal/applications/${application.applicationId}/history`));
    } catch (requestError) {
      setHistoryError(requestError.message || "Could not load application history.");
    }
  }

  return <>
    <PageHeader eyebrow="Candidate portal" title="My applications" subtitle="Review application statuses and their recorded history." />
    <Card>
      <DataTable loading={loading} error={error} rows={data.content || []} empty="You have not applied to any jobs yet." columns={[
        { key: "jobTitle", label: "Position", render: (row) => <div className="primary-cell"><strong>{row.jobTitle}</strong><span>{row.companyName}</span></div> },
        { key: "appliedAt", label: "Applied", render: (row) => row.appliedAt ? new Date(row.appliedAt).toLocaleDateString() : "—" },
        { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
        { key: "statusReason", label: "Latest update", render: (row) => row.statusReason || "—" },
        { key: "history", label: "History", render: (row) => <Button variant="secondary" small onClick={() => showHistory(row)}>View history</Button> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={PAGE_SIZE} onPageChange={setPage} />
    </Card>
    {history && <Modal title="Application status history" onClose={() => setHistory(null)}>
      {historyError ? <div className="error-state">{historyError}</div>
        : history.length ? <div className="card-body activity-list">{history.map((item) => (
          <div className="activity-item" key={item.historyId}>
            <span className="activity-dot" /><div className="activity-copy">
              <strong>{item.previousStatus ? `${item.previousStatus} → ` : ""}{item.newStatus}</strong>
              <br />{item.changedAt ? new Date(item.changedAt).toLocaleString() : ""}
              {item.changeReason ? ` · ${item.changeReason}` : ""}
            </div>
          </div>
        ))}</div> : <div className="empty-state">No status changes have been recorded.</div>}
    </Modal>}
  </>;
}

export function CandidateInterviewsPage() {
  const { data, page, setPage, loading, error } = usePortalPage("/portal/interviews");
  return <>
    <PageHeader eyebrow="Candidate portal" title="My interviews" subtitle="View interview schedules and meeting information for your applications." />
    <Card><DataTable loading={loading} error={error} rows={data.content || []} empty="No interviews are scheduled for your applications." columns={[
      { key: "scheduledAt", label: "Date and time", render: (row) => row.scheduledAt ? new Date(row.scheduledAt).toLocaleString() : "—" },
      { key: "jobTitle", label: "Position", render: (row) => <div className="primary-cell"><strong>{row.jobTitle}</strong><span>{row.companyName}</span></div> },
      { key: "interviewRound", label: "Round" },
      { key: "interviewMode", label: "Mode" },
      { key: "location", label: "Location / link", render: (row) => row.meetingLink || row.location || "—" },
      { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
    ]} /><Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={PAGE_SIZE} onPageChange={setPage} /></Card>
  </>;
}

export function CandidateOffersPage() {
  const { data, page, setPage, loading, error } = usePortalPage("/portal/offers");
  return <>
    <PageHeader eyebrow="Candidate portal" title="My offers" subtitle="Track offer decisions and joining details." />
    <Card><DataTable loading={loading} error={error} rows={data.content || []} empty="No offers are available for your applications." columns={[
      { key: "designation", label: "Role" },
      { key: "companyName", label: "Company" },
      { key: "packageAmount", label: "Package", render: (row) => row.packageAmount == null ? "—" : row.packageAmount },
      { key: "offerDate", label: "Offer date" },
      { key: "expiryDate", label: "Expires" },
      { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
      { key: "joiningDate", label: "Joining date", render: (row) => row.joiningDate || "—" },
    ]} /><Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={PAGE_SIZE} onPageChange={setPage} /></Card>
  </>;
}
