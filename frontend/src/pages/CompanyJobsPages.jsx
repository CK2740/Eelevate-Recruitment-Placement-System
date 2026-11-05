import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../services/api.js";
import { Badge, Button, Card, DataTable, Field, Modal, PageHeader, Pagination, ToastRegion, useToast } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";
import {
  defaultJobStatus,
  jobRequestFromForm,
  jobSubmissionError,
  loadJobCompanies,
  pageForSavedJob,
  saveJob,
} from "./jobForm.js";

const SIZE = 10;

function CompanyForm({ record, onClose, onSaved }) {
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const body = {
      companyName: form.get("companyName").trim(),
      industry: form.get("industry").trim() || null,
      location: form.get("location").trim() || null,
      contactPerson: form.get("contactPerson").trim() || null,
      email: form.get("email").trim() || null,
      mobile: form.get("mobile").trim() || null,
      website: form.get("website").trim() || null,
      status: form.get("status"),
    };
    try {
      if (record) await api.put(`/companies/${record.companyId}`, body);
      else await api.post("/companies", body);
      onSaved();
      onClose();
    } catch (error) { window.dispatchEvent(new CustomEvent("app-toast", { detail: { message: error.message, error: true } })); }
    finally { setBusy(false); }
  }
  return <Modal title={record ? "Edit company" : "Add company"} onClose={onClose}>
    <form onSubmit={submit}>
      <div className="form-grid">
        <Field label="Company name" name="companyName" defaultValue={record?.companyName} required maxLength={200} />
        <Field label="Industry" name="industry" defaultValue={record?.industry} maxLength={120} />
        <Field label="Location" name="location" defaultValue={record?.location} maxLength={200} />
        <Field label="Contact person" name="contactPerson" defaultValue={record?.contactPerson} maxLength={160} />
        <Field label="Email" name="email" type="email" defaultValue={record?.email} maxLength={254} />
        <Field label="Phone" name="mobile" type="tel" defaultValue={record?.mobile} maxLength={30} />
        <Field label="Website" name="website" type="url" defaultValue={record?.website} placeholder="https://example.com" className="span-all" />
        <Field label="Status" name="status" as="select" defaultValue={record?.status || "ACTIVE"}>
          <option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
        </Field>
      </div>
      <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={busy}>{busy ? "Saving…" : "Save company"}</Button></div>
    </form>
  </Modal>;
}

export function CompaniesPage() {
  const { can } = useAuth();
  const [filters, setFilters] = useState({ search: "", industry: "", location: "", status: "" });
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(null);
  const [modal, setModal] = useState(false);
  const toast = useToast();

  async function load() {
    setLoading(true); setError("");
    try { setData(await api.get("/companies", { ...filters, page, size: SIZE })); }
    catch (requestError) { setError(requestError.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [page, filters.search, filters.industry, filters.location, filters.status]);
  useEffect(() => {
    const listener = (event) => toast.push(event.detail.message, event.detail.error);
    window.addEventListener("app-toast", listener);
    return () => window.removeEventListener("app-toast", listener);
  }, []);

  async function remove(company) {
    if (!window.confirm(`Delete ${company.companyName}? Companies linked to jobs cannot be deleted.`)) return;
    try { await api.delete(`/companies/${company.companyId}`); toast.push("Company deleted."); load(); }
    catch (requestError) { toast.push(requestError.message, true); }
  }

  return <>
    <PageHeader eyebrow="Partner directory" title="Companies" subtitle="Manage hiring partners and their contact details."
      action={can("manageCompanies") && <Button onClick={() => { setEditing(null); setModal(true); }}>＋ Add company</Button>} />
    <Card>
      <div className="toolbar">
        <input className="search-field" aria-label="Search companies" placeholder="Search company name…" value={filters.search} onChange={(event) => { setPage(0); setFilters({ ...filters, search: event.target.value }); }} />
        <input aria-label="Filter by industry" placeholder="Industry" value={filters.industry} onChange={(event) => { setPage(0); setFilters({ ...filters, industry: event.target.value }); }} />
        <input aria-label="Filter by location" placeholder="Location" value={filters.location} onChange={(event) => { setPage(0); setFilters({ ...filters, location: event.target.value }); }} />
        <select aria-label="Filter companies by status" value={filters.status} onChange={(event) => { setPage(0); setFilters({ ...filters, status: event.target.value }); }}>
          <option value="">All statuses</option><option>ACTIVE</option><option>INACTIVE</option>
        </select>
      </div>
      <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "companyName", label: "Company", render: (row) => <div className="primary-cell"><strong>{row.companyName}</strong><span>{row.industry || "Industry not specified"}</span></div> },
        { key: "location", label: "Location" },
        { key: "contactPerson", label: "Contact", render: (row) => <div className="primary-cell"><strong>{row.contactPerson || "—"}</strong><span>{row.email || row.mobile || "No contact details"}</span></div> },
        { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
        { key: "actions", label: "Actions", render: (row) => <div className="table-actions">{can("manageCompanies") && <>
          <Button variant="secondary" small onClick={() => { setEditing(row); setModal(true); }}>Edit</Button>
          <Button variant="danger" small onClick={() => remove(row)}>Delete</Button>
        </>}</div> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={SIZE} onPageChange={setPage} />
    </Card>
    {modal && <CompanyForm record={editing} onClose={() => setModal(false)} onSaved={() => { toast.push("Company saved."); load(); }} />}
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

function JobForm({ record, onClose, onSaved, onError, inline = false }) {
  const { can } = useAuth();
  const [companies, setCompanies] = useState([]);
  const [companiesLoading, setCompaniesLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [companyError, setCompanyError] = useState("");
  const [submissionErrors, setSubmissionErrors] = useState([]);
  useEffect(() => {
    loadJobCompanies(api, record)
      .then(setCompanies)
      .catch((error) => setCompanyError(error.message || "Could not load companies."))
      .finally(() => setCompaniesLoading(false));
  }, [record]);

  async function submit(event) {
    event.preventDefault();
    setSubmissionErrors([]);
    setBusy(true);
    try {
      const request = jobRequestFromForm(new FormData(event.currentTarget));
      const saved = await saveJob(api, record, request);
      onSaved(saved);
    } catch (error) {
      const messages = jobSubmissionError(error);
      setSubmissionErrors(messages);
      onError?.(messages[0]);
    } finally {
      setBusy(false);
    }
  }

  const today = new Date();
  const minimumDeadline = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const content = <>
    {companyError && <p className="error-state">{companyError}</p>}
    {!companyError && !companiesLoading && companies.length === 0
      && <p className="error-state" role="alert">
        No active companies are available. {can("manageCompanies")
          ? <>Add or activate a company in <Link to="/companies.html">Company Management</Link> before creating a job.</>
          : "Ask an administrator to add or activate a company before creating a job."}
      </p>}
    {submissionErrors.length > 0 && <div className="error-state" role="alert" aria-live="assertive">
      <strong>Job could not be saved.</strong>
      <ul>{submissionErrors.map((message, index) => <li key={`${index}-${message}`}>{message}</li>)}</ul>
    </div>}
    <form onSubmit={submit} onInvalid={() => setSubmissionErrors(["Complete all required fields and correct any invalid values."])}>
      <div className="form-grid">
        <Field label="Company" name="companyId" as="select" required disabled={companiesLoading || Boolean(companyError)}
          defaultValue={record?.companyId || ""}>
          <option value="" disabled>{companiesLoading ? "Loading companies…" : "Select company"}</option>
          {companies.map((company) => <option key={company.companyId} value={company.companyId}>{company.companyName}</option>)}
        </Field>
        <Field label="Job title" name="jobTitle" defaultValue={record?.jobTitle} required maxLength={180} />
        <Field label="Technology" name="technology" defaultValue={record?.technology} required maxLength={180} />
        <Field label="Location" name="location" defaultValue={record?.location} required maxLength={200} />
        <Field label="Openings" name="openings" type="number" min="1" defaultValue={record?.openings || 1} required />
        <Field label="Annual package" name="packageAmount" type="number" min="0" step="0.01" defaultValue={record?.packageAmount ?? 0} required />
        <Field label="Application deadline" name="applicationDeadline" type="date" min={minimumDeadline} defaultValue={record?.applicationDeadline} required />
        <Field label="Employment type" name="employmentType" as="select" defaultValue={record?.employmentType || "FULL_TIME"}>
          {["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP"].map((item) => <option key={item}>{item}</option>)}
        </Field>
        <Field label="Status" name="status" as="select" defaultValue={defaultJobStatus(record)}>
          {["DRAFT", "OPEN", "CLOSED"].map((item) => <option key={item}>{item}</option>)}
        </Field>
        <Field label="Required qualification" name="requiredQualification" defaultValue={record?.requiredQualification} maxLength={160} />
        <Field label="Minimum percentage" name="minimumPercentage" type="number" min="0" max="100" step="0.01" defaultValue={record?.eligibilityCriteria?.minimumPercentage} />
        <Field label="Maximum backlogs" name="maximumBacklogs" type="number" min="0" defaultValue={record?.eligibilityCriteria?.maximumBacklogs ?? 0} />
        <Field label="Passing year from" name="minimumPassingYear" type="number" min="1901" max="2155" defaultValue={record?.eligibilityCriteria?.minimumPassingYear} />
        <Field label="Passing year to" name="maximumPassingYear" type="number" min="1901" max="2155" defaultValue={record?.eligibilityCriteria?.maximumPassingYear} />
        <Field label="Skills" name="skills" as="textarea" className="span-all" defaultValue={record?.skills} maxLength={5000} />
        <Field label="Job description" name="jobDescription" as="textarea" className="span-all" defaultValue={record?.jobDescription} maxLength={10000} />
      </div>
      <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
        <Button type="submit" disabled={busy || companiesLoading || companies.length === 0 || Boolean(companyError)}>
          {busy ? "Saving…" : "Save job"}
        </Button>
      </div>
    </form>
  </>;
  return inline
    ? <Card title={record ? "Job details" : "Job details"}><div className="card-body">{content}</div></Card>
    : <Modal title={record ? "Edit job" : "Create a job"} onClose={onClose}>{content}</Modal>;
}

export function JobsPage() {
  const { can } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [filters, setFilters] = useState({
    search: "", technology: "", location: "", status: "", jobType: "", passingYear: "",
  });
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const toast = useToast();

  async function load() {
    const savedJobId = location.state?.savedJobId;
    setLoading(true); setError("");
    try {
      const result = await api.get("/jobs", { ...filters, page, size: SIZE });
      setData(result);
      if (savedJobId) {
        const targetPage = pageForSavedJob(result.totalElements, SIZE, result.content || [], savedJobId);
        if (targetPage !== null && targetPage !== page) setPage(targetPage);
      }
    }
    catch (requestError) { setError(requestError.message); }
    finally { setLoading(false); }
  }
  useEffect(() => {
    load();
  }, [page, filters.search, filters.technology, filters.location, filters.status, filters.jobType, filters.passingYear]);
  useEffect(() => {
    const listener = (event) => toast.push(event.detail.message, event.detail.error);
    window.addEventListener("app-toast", listener);
    return () => window.removeEventListener("app-toast", listener);
  }, []);
  useEffect(() => {
    if (!location.state?.jobSaved) return;
    toast.push(location.state.jobSaved);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.key]);

  async function remove(job) {
    if (!window.confirm(`Delete ${job.jobTitle}? Jobs with applications cannot be deleted.`)) return;
    try { await api.delete(`/jobs/${job.jobId}`); toast.push("Job deleted."); load(); }
    catch (requestError) { toast.push(requestError.message, true); }
  }

  return <>
    <PageHeader eyebrow="Opportunities" title="Jobs" subtitle="Manage open roles and monitor hiring activity."
      action={can("manageJobs") && <Link className="btn btn-primary" to="/job-form.html">＋ Create job</Link>} />
    <Card>
      <div className="toolbar">
        <input className="search-field" aria-label="Search jobs" placeholder="Search job, company or technology…" value={filters.search} onChange={(event) => { setPage(0); setFilters({ ...filters, search: event.target.value }); }} />
        <input aria-label="Filter jobs by technology" placeholder="Technology" value={filters.technology} onChange={(event) => { setPage(0); setFilters({ ...filters, technology: event.target.value }); }} />
        <input aria-label="Filter jobs by location" placeholder="Location" value={filters.location} onChange={(event) => { setPage(0); setFilters({ ...filters, location: event.target.value }); }} />
        <select aria-label="Filter jobs by type" value={filters.jobType} onChange={(event) => { setPage(0); setFilters({ ...filters, jobType: event.target.value }); }}>
          <option value="">All job types</option>{["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP"].map((item) => <option key={item} value={item}>{item.replaceAll("_", " ")}</option>)}
        </select>
        <input aria-label="Filter jobs by passing year" placeholder="Passing year" type="number" value={filters.passingYear} onChange={(event) => { setPage(0); setFilters({ ...filters, passingYear: event.target.value }); }} />
        <select aria-label="Filter jobs by status" value={filters.status} onChange={(event) => { setPage(0); setFilters({ ...filters, status: event.target.value }); }}>
          <option value="">All statuses</option>{["DRAFT", "OPEN", "CLOSED"].map((status) => <option key={status}>{status}</option>)}
        </select>
      </div>
      <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "jobTitle", label: "Role", render: (row) => <div className="primary-cell"><strong>{row.jobTitle}</strong><span>{row.companyName}</span></div> },
        { key: "technology", label: "Technology" },
        { key: "location", label: "Location" },
        { key: "employmentType", label: "Type", render: (row) => row.employmentType?.replaceAll("_", " ") },
        { key: "applicationDeadline", label: "Deadline" },
        { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
        { key: "actions", label: "Actions", render: (row) => <div className="table-actions">{can("manageJobs") && <>
          <Link className="btn btn-secondary btn-small" to={`/job-form.html?id=${row.jobId}`}>Edit</Link>
          <Button variant="danger" small onClick={() => remove(row)}>Delete</Button>
        </>}</div> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={SIZE} onPageChange={setPage} />
    </Card>
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

export function JobFormPage() {
  const navigate = useNavigate();
  const [query] = useSearchParams();
  const id = query.get("id");
  const { can } = useAuth();
  const [record, setRecord] = useState(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (id) api.get(`/jobs/${id}`).then(setRecord).catch((requestError) => setError(requestError.message));
  }, [id]);

  if (!can("manageJobs")) return <PageHeader title="Access restricted" subtitle="Your role does not have permission to manage jobs." />;
  if (error) return <div className="error-state card">{error}</div>;
  if (id && !record) return <div className="loading-state">Loading job…</div>;
  return <>
    <PageHeader eyebrow="Opportunities" title={id ? "Edit job" : "Create a job"} subtitle="Set the role details and candidate eligibility criteria." />
    <JobForm inline record={record} onClose={() => navigate("/jobs.html")}
      onSaved={(saved) => navigate("/jobs.html", {
        state: {
          jobSaved: id ? "Job updated successfully." : "Job created successfully.",
          ...(!id && saved?.jobId ? { savedJobId: saved.jobId } : {}),
        },
      })} />
  </>;
}
