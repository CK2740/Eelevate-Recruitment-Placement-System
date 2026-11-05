import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../services/api.js";
import { Badge, Button, Card, DataTable, Field, Modal, PageHeader, Pagination, StatusSelect, ToastRegion, useToast } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";

const SIZE = 10;

function usePagedData(path, filters, reloadKey = 0) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const filterKey = JSON.stringify(filters);
  async function load() {
    setLoading(true); setError("");
    try { setData(await api.get(path, { ...filters, page, size: SIZE })); }
    catch (requestError) { setError(requestError.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [filterKey, page, reloadKey]);
  return { page, setPage, data, loading, error, load };
}

function ApplyModal({ onClose, onSaved }) {
  const [candidates, setCandidates] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    Promise.all([api.get("/candidates", { size: 100 }), api.get("/jobs", { status: "OPEN", size: 100 })])
      .then(([candidatePage, jobPage]) => {
        setCandidates(candidatePage.content || []);
        setJobs(jobPage.content || []);
      })
      .catch((requestError) => setError(requestError.message));
  }, []);

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError("");
    const form = new FormData(event.currentTarget);
    const jobId = Number(form.get("jobId"));
    try {
      await api.post(`/jobs/${jobId}/applications`, { candidateId: Number(form.get("candidateId")) });
      onSaved(); onClose();
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }
  return <Modal title="Start an application" onClose={onClose}>
    {error && <div className="error-state">{error}</div>}
    <form onSubmit={submit}>
      <div className="form-grid">
        <Field label="Open job" name="jobId" as="select" required defaultValue="">
          <option value="" disabled>Select a job</option>{jobs.map((job) => <option key={job.jobId} value={job.jobId}>{job.jobTitle} · {job.companyName}</option>)}
        </Field>
        <Field label="Candidate" name="candidateId" as="select" required defaultValue="">
          <option value="" disabled>Select a candidate</option>{candidates.map((candidate) => <option key={candidate.candidateId} value={candidate.candidateId}>{candidate.fullName} · {candidate.qualification}</option>)}
        </Field>
      </div>
      <p className="small-note" style={{ marginTop: 14 }}>Eligibility criteria and the application deadline are validated by the backend when submitted.</p>
      <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={busy || jobs.length === 0 || candidates.length === 0}>{busy ? "Submitting…" : "Submit application"}</Button></div>
    </form>
  </Modal>;
}

export function ApplicationsPage() {
  const { can } = useAuth();
  const [query] = useSearchParams();
  const [filters, setFilters] = useState(() => ({
    search: "", candidate: query.get("candidate") || "", company: "", status: "", passingYear: "",
  }));
  const [reloadKey, setReloadKey] = useState(0);
  const { page, setPage, data, loading, error, load } = usePagedData("/applications", filters, reloadKey);
  const [applyOpen, setApplyOpen] = useState(false);
  const [updating, setUpdating] = useState(null);
  const [reason, setReason] = useState("");
  const [nextStatus, setNextStatus] = useState("");
  const toast = useToast();
  const mayChange = can("manageApplicationStatuses");

  async function saveStatus(application) {
    try {
      await api.put(`/applications/${application.applicationId}/status`, { status: nextStatus, reason: reason || null });
      toast.push("Application status updated.");
      setUpdating(null); setReason(""); setReloadKey((key) => key + 1);
    } catch (requestError) { toast.push(requestError.message, true); }
  }

  return <>
    <PageHeader eyebrow="Talent pipeline" title="Applications" subtitle="Review candidate applications and progress through the selection workflow."
      action={can("createApplications") && <Button onClick={() => setApplyOpen(true)}>＋ New application</Button>} />
    <Card>
      <div className="toolbar">
        <input className="search-field" aria-label="Search applications" placeholder="Search role or company…" value={filters.search} onChange={(event) => { setPage(0); setFilters({ ...filters, search: event.target.value }); }} />
        <input aria-label="Filter by candidate" placeholder="Candidate" value={filters.candidate} onChange={(event) => { setPage(0); setFilters({ ...filters, candidate: event.target.value }); }} />
        <input aria-label="Filter by company" placeholder="Company" value={filters.company} onChange={(event) => { setPage(0); setFilters({ ...filters, company: event.target.value }); }} />
        <input aria-label="Filter by passing year" placeholder="Passing year" type="number" value={filters.passingYear} onChange={(event) => { setPage(0); setFilters({ ...filters, passingYear: event.target.value }); }} />
        <select aria-label="Filter by application status" value={filters.status} onChange={(event) => { setPage(0); setFilters({ ...filters, status: event.target.value }); }}>
          <option value="">All stages</option>{["APPLIED", "SHORTLISTED", "INTERVIEW", "SELECTED", "OFFERED", "JOINED", "REJECTED"].map((item) => <option key={item}>{item}</option>)}
        </select>
      </div>
      <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "candidateName", label: "Candidate", render: (row) => <div className="primary-cell"><strong>{row.candidateName}</strong><span>Application #{row.applicationId}</span></div> },
        { key: "jobTitle", label: "Position", render: (row) => <div className="primary-cell"><strong>{row.jobTitle}</strong><span>{row.companyName}</span></div> },
        { key: "appliedAt", label: "Applied", render: (row) => new Date(row.appliedAt).toLocaleDateString() },
        { key: "status", label: "Stage", render: (row) => <Badge value={row.status} /> },
        { key: "actions", label: "Actions", render: (row) => can("manageApplicationStatuses") && <Button variant="secondary" small onClick={() => { setUpdating(row); setNextStatus(row.status); }}>Update status</Button> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={SIZE} onPageChange={setPage} />
    </Card>
    {applyOpen && <ApplyModal onClose={() => setApplyOpen(false)} onSaved={() => { toast.push("Application submitted. Eligibility is checked by the server."); setReloadKey((key) => key + 1); }} />}
    {updating && <Modal title={`Update ${updating.candidateName}'s application`} onClose={() => setUpdating(null)}>
      <div className="field"><label htmlFor="application-status">New stage</label>
        <StatusSelect name="application-status" value={nextStatus} options={["APPLIED", "SHORTLISTED", "INTERVIEW", "SELECTED", "OFFERED", "JOINED", "REJECTED"]} onChange={setNextStatus} />
      </div>
      <div className="field" style={{ marginTop: 14 }}><label htmlFor="application-reason">Reason (required for rejection)</label><textarea id="application-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} /></div>
      <div className="form-actions"><Button variant="secondary" onClick={() => setUpdating(null)}>Cancel</Button><Button onClick={() => saveStatus(updating)}>Save status</Button></div>
    </Modal>}
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

function InterviewScheduleModal({ onClose, onSaved }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [mode, setMode] = useState("VIDEO");
  const [interviewers, setInterviewers] = useState([{ id: 1 }]);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError("");
    const form = new FormData(event.currentTarget);
    const applicationId = Number(form.get("applicationId"));
    const body = {
      applicationId,
      interviewRound: form.get("interviewRound").trim(),
      interviewType: form.get("interviewType"),
      roundNumber: Number(form.get("roundNumber")),
      scheduledAt: `${form.get("scheduledAt")}:00`,
      durationMinutes: Number(form.get("durationMinutes")),
      interviewMode: form.get("interviewMode"),
      location: form.get("location").trim() || null,
      meetingLink: form.get("meetingLink").trim() || null,
      interviewers: interviewers.map(({ id }) => ({
        name: form.get(`interviewerName${id}`).trim(),
        email: form.get(`interviewerEmail${id}`).trim() || null,
      })),
    };
    try {
      await api.post(`/applications/${applicationId}/interviews`, body);
      onSaved(); onClose();
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }
  return <Modal title="Schedule an interview" onClose={onClose}>
    {error && <div className="error-state">{error}</div>}
    <form onSubmit={submit}>
      <div className="form-grid">
        <Field label="Application ID" name="applicationId" type="number" min="1" required />
        <Field label="Round name" name="interviewRound" placeholder="Technical round" required />
        <Field label="Interview type" name="interviewType" as="select">{["SCREENING", "TECHNICAL", "HR", "MANAGERIAL", "FINAL", "OTHER"].map((item) => <option key={item}>{item}</option>)}</Field>
        <Field label="Round number" name="roundNumber" type="number" min="1" max="20" defaultValue="1" required />
        <Field label="Date and time" name="scheduledAt" type="datetime-local" min={new Date().toISOString().slice(0, 16)} required />
        <Field label="Duration (minutes)" name="durationMinutes" type="number" min="15" max="480" defaultValue="60" required />
        <Field label="Mode" name="interviewMode" as="select" value={mode} onChange={(event) => setMode(event.target.value)}>{["VIDEO", "IN_PERSON", "PHONE"].map((item) => <option key={item}>{item}</option>)}</Field>
        {interviewers.map(({ id }, index) => <div className="interviewer-fields span-all" key={id}>
          <div className="interviewer-heading">
            <strong>Interviewer {index + 1}</strong>
            {interviewers.length > 1 && <Button type="button" variant="danger" small onClick={() => setInterviewers((current) => current.filter((entry) => entry.id !== id))}>Remove</Button>}
          </div>
          <div className="form-grid">
            <Field label="Interviewer name" name={`interviewerName${id}`} required />
            <Field label="Interviewer email" name={`interviewerEmail${id}`} type="email" />
          </div>
        </div>)}
        <div className="span-all"><Button type="button" variant="secondary" small onClick={() => setInterviewers((current) => [...current, { id: Math.max(...current.map((item) => item.id)) + 1 }])}>＋ Add interviewer</Button></div>
        {mode === "IN_PERSON" && <Field label="Location" name="location" className="span-all" required />}
        {mode === "VIDEO" && <Field label="Meeting link" name="meetingLink" type="url" className="span-all" required />}
      </div>
      <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={busy}>{busy ? "Scheduling…" : "Schedule interview"}</Button></div>
    </form>
  </Modal>;
}

function FeedbackModal({ interview, onClose, onSaved }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [interviewerId, setInterviewerId] = useState(interview.interviewers?.[0]?.interviewerId || "");
  const interviewer = interview.interviewers?.find((item) => item.interviewerId === Number(interviewerId));
  if (!interview.interviewers?.length) return <Modal title="Interviewer feedback" onClose={onClose}><p className="error-state">No interviewer is associated with this interview.</p></Modal>;
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError("");
    const form = new FormData(event.currentTarget);
    const submission = [{
      interviewerId: Number(form.get("interviewerId")),
      feedback: {
        technicalRating: form.get("technicalRating") ? Number(form.get("technicalRating")) : null,
        communicationRating: form.get("communicationRating") ? Number(form.get("communicationRating")) : null,
        recommendation: form.get("recommendation"),
        comments: form.get("comments").trim() || null,
      },
    }];
    try { await api.put(`/interviews/${interview.interviewId}/feedback`, submission); onSaved(); onClose(); }
    catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }
  return <Modal title={`Feedback · ${interview.candidateName}`} onClose={onClose}>
    {error && <div className="error-state">{error}</div>}
    <form onSubmit={submit}>
      <Field label="Interviewer" name="interviewerId" as="select" value={interviewerId} onChange={(event) => setInterviewerId(event.target.value)}>
        {interview.interviewers.map((item) => <option value={item.interviewerId} key={item.interviewerId}>{item.interviewerName}</option>)}
      </Field>
      <div className="form-grid">
        <Field label="Technical rating (0–10)" name="technicalRating" type="number" min="0" max="10" step="0.1" defaultValue={interviewer.technicalRating} />
        <Field label="Communication rating (0–10)" name="communicationRating" type="number" min="0" max="10" step="0.1" defaultValue={interviewer.communicationRating} />
        <Field label="Recommendation" name="recommendation" as="select" defaultValue={interviewer.recommendation || "ON_HOLD"}>
          {["PASS", "FAIL", "ON_HOLD"].map((item) => <option key={item}>{item}</option>)}
        </Field>
        <Field label="Comments" name="comments" as="textarea" className="span-all" defaultValue={interviewer.comments} maxLength={10000} />
      </div>
      <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={busy}>{busy ? "Saving…" : "Save feedback"}</Button></div>
    </form>
  </Modal>;
}

export function InterviewsPage() {
  const { can } = useAuth();
  const [filters, setFilters] = useState({ search: "", status: "", mode: "", from: "", to: "" });
  const [reloadKey, setReloadKey] = useState(0);
  const { page, setPage, data, loading, error } = usePagedData("/interviews", {
    search: filters.search,
    status: filters.status,
    mode: filters.mode,
    from: filters.from ? `${filters.from}T00:00:00` : "",
    to: filters.to ? `${filters.to}T23:59:59` : "",
  }, reloadKey);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [feedbackInterview, setFeedbackInterview] = useState(null);
  const [statusInterview, setStatusInterview] = useState(null);
  const [status, setStatus] = useState("");
  const [reason, setReason] = useState("");
  const toast = useToast();
  const canSchedule = can("scheduleInterviews");

  async function updateStatus() {
    try {
      await api.patch(`/interviews/${statusInterview.interviewId}/status`, { status, reason: reason || null });
      toast.push("Interview status updated.");
      setStatusInterview(null); setReloadKey((key) => key + 1);
    } catch (requestError) { toast.push(requestError.message, true); }
  }

  return <>
    <PageHeader eyebrow="Selection process" title="Interviews" subtitle="Coordinate interview rounds and capture interviewer feedback."
      action={canSchedule && <Button onClick={() => setScheduleOpen(true)}>＋ Schedule interview</Button>} />
    <Card>
      <div className="toolbar">
        <input className="search-field" aria-label="Search interviews" placeholder="Search candidate, role or company…" value={filters.search} onChange={(event) => { setPage(0); setFilters({ ...filters, search: event.target.value }); }} />
        <select aria-label="Filter interview status" value={filters.status} onChange={(event) => { setPage(0); setFilters({ ...filters, status: event.target.value }); }}>
          <option value="">All statuses</option>{["SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"].map((item) => <option key={item}>{item}</option>)}
        </select>
        <select aria-label="Filter interview mode" value={filters.mode} onChange={(event) => { setPage(0); setFilters({ ...filters, mode: event.target.value }); }}>
          <option value="">All modes</option>{["VIDEO", "IN_PERSON", "PHONE"].map((item) => <option key={item}>{item}</option>)}
        </select>
        <input aria-label="From date" type="date" value={filters.from} onChange={(event) => { setPage(0); setFilters({ ...filters, from: event.target.value }); }} />
        <input aria-label="To date" type="date" value={filters.to} onChange={(event) => { setPage(0); setFilters({ ...filters, to: event.target.value }); }} />
      </div>
      <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "candidateName", label: "Candidate", render: (row) => <div className="primary-cell"><strong>{row.candidateName}</strong><span>{row.companyName}</span></div> },
        { key: "jobTitle", label: "Role", render: (row) => <div className="primary-cell"><strong>{row.jobTitle}</strong><span>{row.interviewRound} · Round {row.roundNumber}</span></div> },
        { key: "scheduledAt", label: "Schedule", render: (row) => <div className="primary-cell"><strong>{new Date(row.scheduledAt).toLocaleDateString()}</strong><span>{new Date(row.scheduledAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} · {row.durationMinutes} min</span></div> },
        { key: "interviewMode", label: "Mode" },
        { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
        { key: "actions", label: "Actions", render: (row) => <div className="table-actions">
          {can("manageInterviewFeedback") && <Button variant="secondary" small onClick={() => setFeedbackInterview(row)}>Feedback</Button>}
          {can("manageInterviewStatuses") && <Button variant="secondary" small onClick={() => { setStatusInterview(row); setStatus(row.status); }}>Status</Button>}
        </div> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={SIZE} onPageChange={setPage} />
    </Card>
    {scheduleOpen && <InterviewScheduleModal onClose={() => setScheduleOpen(false)} onSaved={() => { toast.push("Interview scheduled."); setReloadKey((key) => key + 1); }} />}
    {feedbackInterview && <FeedbackModal interview={feedbackInterview} onClose={() => setFeedbackInterview(null)} onSaved={() => { toast.push("Feedback saved."); setReloadKey((key) => key + 1); }} />}
    {statusInterview && <Modal title="Update interview status" onClose={() => setStatusInterview(null)}>
      <div className="field"><label htmlFor="interview-status">Status</label><StatusSelect name="interview-status" value={status} options={["SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"]} onChange={setStatus} /></div>
      <div className="field" style={{ marginTop: 14 }}><label htmlFor="interview-reason">Reason (if applicable)</label><textarea id="interview-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} /></div>
      <div className="form-actions"><Button variant="secondary" onClick={() => setStatusInterview(null)}>Cancel</Button><Button onClick={updateStatus}>Save status</Button></div>
    </Modal>}
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}

function OfferModal({ onClose, onSaved }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault(); setBusy(true);
    const form = new FormData(event.currentTarget);
    const applicationId = Number(form.get("applicationId"));
    const body = {
      designation: form.get("designation").trim(),
      packageAmount: Number(form.get("packageAmount")),
      offerDate: form.get("offerDate"),
      expiryDate: form.get("expiryDate"),
      offerDetails: form.get("offerDetails").trim() || null,
    };
    try { await api.post(`/applications/${applicationId}/offers`, body); onSaved(); onClose(); }
    catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }
  return <Modal title="Create an offer" onClose={onClose}>
    {error && <div className="error-state">{error}</div>}
    <form onSubmit={submit}><div className="form-grid">
      <Field label="Application ID" name="applicationId" type="number" min="1" required />
      <Field label="Job title / designation" name="designation" required maxLength={180} />
      <Field label="Package" name="packageAmount" type="number" min="0" step="0.01" required />
      <Field label="Offer date" name="offerDate" type="date" min={new Date().toISOString().slice(0, 10)} required />
      <Field label="Expiry date" name="expiryDate" type="date" min={new Date().toISOString().slice(0, 10)} required />
      <Field label="Offer details" name="offerDetails" as="textarea" className="span-all" maxLength={10000} />
    </div><div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={busy}>{busy ? "Creating…" : "Create offer"}</Button></div></form>
  </Modal>;
}

function PlacementModal({ onClose, onSaved }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault(); setBusy(true);
    const form = new FormData(event.currentTarget);
    const applicationId = Number(form.get("applicationId"));
    const request = {
      designation: form.get("designation").trim(),
      finalPackage: Number(form.get("finalPackage")),
      actualJoiningDate: form.get("actualJoiningDate") || null,
      joiningStatus: form.get("joiningStatus"),
    };
    try { await api.post(`/applications/${applicationId}/placements`, request); onSaved(); onClose(); }
    catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }
  return <Modal title="Create placement record" onClose={onClose}>
    {error && <div className="error-state">{error}</div>}
    <form onSubmit={submit}><div className="form-grid">
      <Field label="Application ID (accepted offer)" name="applicationId" type="number" min="1" required />
      <Field label="Designation" name="designation" required maxLength={180} />
      <Field label="Final package" name="finalPackage" type="number" min="0" step="0.01" required />
      <Field label="Joining date" name="actualJoiningDate" type="date" />
      <Field label="Joining status" name="joiningStatus" as="select" defaultValue="PENDING">{["PENDING", "JOINED", "NOT_JOINED"].map((item) => <option key={item}>{item}</option>)}</Field>
    </div><div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={busy}>{busy ? "Saving…" : "Create placement"}</Button></div></form>
  </Modal>;
}

export function PlacementProcessPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState("offers");
  const [filters, setFilters] = useState({ search: "", status: "" });
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [modal, setModal] = useState("");
  const [statusOffer, setStatusOffer] = useState(null);
  const [nextStatus, setNextStatus] = useState("");
  const [reason, setReason] = useState("");
  const [joiningDate, setJoiningDate] = useState("");
  const [statusPlacement, setStatusPlacement] = useState(null);
  const [placementStatus, setPlacementStatus] = useState("");
  const [placementReason, setPlacementReason] = useState("");
  const toast = useToast();

  async function load() {
    setLoading(true); setError("");
    try {
      const path = tab === "offers" ? "/offers" : "/placements";
      setData(await api.get(path, { ...filters, page, size: SIZE }));
    } catch (requestError) { setError(requestError.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [tab, page, filters.search, filters.status, reloadKey]);

  async function updateOffer() {
    try {
      await api.patch(`/offers/${statusOffer.offerId}/status`, {
        status: nextStatus,
        reason: reason || null,
        joiningDate: joiningDate || null,
      });
      toast.push("Offer status updated.");
      setStatusOffer(null); setReloadKey((key) => key + 1);
    } catch (requestError) { toast.push(requestError.message, true); }
  }
  async function updatePlacement() {
    try {
      await api.put(`/placements/${statusPlacement.placementId}?reason=${encodeURIComponent(placementReason || "Status updated")}`, {
        designation: statusPlacement.designation,
        finalPackage: statusPlacement.finalPackage,
        actualJoiningDate: statusPlacement.actualJoiningDate || null,
        joiningStatus: placementStatus,
      });
      toast.push("Placement status updated.");
      setStatusPlacement(null); setReloadKey((key) => key + 1);
    } catch (requestError) { toast.push(requestError.message, true); }
  }

  return <>
    <PageHeader eyebrow="Successful outcomes" title="Offers & placement" subtitle="Follow offers from decision to candidate joining."
      action={tab === "offers" && can("manageOffers") ? <Button onClick={() => setModal("offer")}>＋ Create offer</Button>
        : tab === "placements" && can("managePlacements") ? <Button onClick={() => setModal("placement")}>＋ Add placement</Button> : null} />
    <div className="tab-bar">
      <button className={`tab-button${tab === "offers" ? " active" : ""}`} onClick={() => { setTab("offers"); setPage(0); }}>Offers</button>
      <button className={`tab-button${tab === "placements" ? " active" : ""}`} onClick={() => { setTab("placements"); setPage(0); }}>Placements</button>
    </div>
    <Card>
      <div className="toolbar">
        <input className="search-field" aria-label={`Search ${tab}`} placeholder={tab === "offers" ? "Search candidate, company or role…" : "Search candidate, company or designation…"} value={filters.search} onChange={(event) => { setPage(0); setFilters({ ...filters, search: event.target.value }); }} />
        <select aria-label={`Filter ${tab} by status`} value={filters.status} onChange={(event) => { setPage(0); setFilters({ ...filters, status: event.target.value }); }}>
          <option value="">All statuses</option>{(tab === "offers" ? ["PENDING", "ACCEPTED", "REJECTED", "EXPIRED", "WITHDRAWN"] : ["PENDING", "JOINED", "NOT_JOINED"]).map((item) => <option key={item}>{item}</option>)}
        </select>
      </div>
      {tab === "offers" ? <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "candidateName", label: "Candidate", render: (row) => <div className="primary-cell"><strong>{row.candidateName}</strong><span>{row.companyName}</span></div> },
        { key: "designation", label: "Role" },
        { key: "packageAmount", label: "Package", render: (row) => row.packageAmount?.toLocaleString() },
        { key: "offerDate", label: "Offer date" },
        { key: "expiryDate", label: "Expires" },
        { key: "joiningDate", label: "Joining date" },
        { key: "status", label: "Status", render: (row) => <Badge value={row.status} /> },
        { key: "actions", label: "Actions", render: (row) => can("manageOffers") && <Button variant="secondary" small onClick={() => { setStatusOffer(row); setNextStatus(row.status); setJoiningDate(row.joiningDate || ""); }}>Update</Button> },
      ]} /> : <DataTable loading={loading} error={error} rows={data.content || []} columns={[
        { key: "candidateName", label: "Candidate", render: (row) => <div className="primary-cell"><strong>{row.candidateName}</strong><span>{row.companyName}</span></div> },
        { key: "designation", label: "Role" },
        { key: "finalPackage", label: "Final package", render: (row) => row.finalPackage?.toLocaleString() },
        { key: "actualJoiningDate", label: "Joined on" },
        { key: "joiningStatus", label: "Status", render: (row) => <Badge value={row.joiningStatus} /> },
        { key: "actions", label: "Actions", render: (row) => can("managePlacements") && <Button variant="secondary" small onClick={() => { setStatusPlacement(row); setPlacementStatus(row.joiningStatus); }}>Update</Button> },
      ]} />}
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} size={SIZE} onPageChange={setPage} />
    </Card>
    {modal === "offer" && <OfferModal onClose={() => setModal("")} onSaved={() => { toast.push("Offer created."); setReloadKey((key) => key + 1); }} />}
    {modal === "placement" && <PlacementModal onClose={() => setModal("")} onSaved={() => { toast.push("Placement record created."); setReloadKey((key) => key + 1); }} />}
    {statusOffer && <Modal title={`Update offer · ${statusOffer.candidateName}`} onClose={() => setStatusOffer(null)}>
      <div className="form-grid">
        <Field label="Offer status" name="offerStatus" as="select" value={nextStatus} onChange={(event) => setNextStatus(event.target.value)}>{["PENDING", "ACCEPTED", "REJECTED", "EXPIRED", "WITHDRAWN"].map((item) => <option key={item}>{item}</option>)}</Field>
        <Field label="Joining date (on acceptance)" name="joiningDate" type="date" value={joiningDate} onChange={(event) => setJoiningDate(event.target.value)} />
        <Field label="Reason (rejection / withdrawal)" name="reason" as="textarea" className="span-all" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} />
      </div>
      <div className="form-actions"><Button variant="secondary" onClick={() => setStatusOffer(null)}>Cancel</Button><Button onClick={updateOffer}>Save</Button></div>
    </Modal>}
    {statusPlacement && <Modal title={`Update placement · ${statusPlacement.candidateName}`} onClose={() => setStatusPlacement(null)}>
      <div className="form-grid">
        <Field label="Joining status" name="placementStatus" as="select" value={placementStatus} onChange={(event) => setPlacementStatus(event.target.value)}>{["PENDING", "JOINED", "NOT_JOINED"].map((item) => <option key={item}>{item}</option>)}</Field>
        <Field label="Actual joining date" name="actualJoiningDate" type="date" value={statusPlacement.actualJoiningDate || ""} onChange={(event) => setStatusPlacement({ ...statusPlacement, actualJoiningDate: event.target.value })} />
        <Field label="Reason" name="placementReason" as="textarea" className="span-all" value={placementReason} onChange={(event) => setPlacementReason(event.target.value)} maxLength={500} />
      </div>
      <div className="form-actions"><Button variant="secondary" onClick={() => setStatusPlacement(null)}>Cancel</Button><Button onClick={updatePlacement}>Save</Button></div>
    </Modal>}
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}
