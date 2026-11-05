import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../services/api.js";
import { Badge, Card, PageHeader } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";
import { dashboardExperienceFor } from "./dashboardExperience.js";
import { reviewRecruiterRequest } from "./userManagement.js";
import { runExclusively } from "../services/interactionActions.js";

const EMPTY = Object.freeze({});

function localDateTime() {
  const date = new Date();
  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function pageCount(page) {
  return Number(page?.totalElements || 0);
}

function request(path, filters) {
  return api.get(path, filters);
}

function requestsForRole(role) {
  const upcomingInterviewFilters = {
    status: "SCHEDULED",
    from: localDateTime(),
    page: 0,
    size: 5,
  };
  if (role === "ADMIN") {
    return {
      metrics: request("/dashboard/metrics"),
      funnel: request("/reports/RECRUITMENT_FUNNEL"),
      audit: request("/audit-logs", { page: 0, size: 6 }),
      pendingRecruiters: request("/auth/admin/recruiters/pending"),
      awaitingApplications: request("/applications", { status: "APPLIED", page: 0, size: 1 }),
      scheduledInterviews: request("/interviews", { ...upcomingInterviewFilters, size: 1 }),
      pendingOffers: request("/offers", { status: "PENDING", page: 0, size: 1 }),
      joinedPlacements: request("/placements", { status: "JOINED", page: 0, size: 1 }),
    };
  }
  if (role === "RECRUITER") {
    return {
      metrics: request("/dashboard/metrics"),
      openJobs: request("/jobs", { status: "OPEN", page: 0, size: 1 }),
      applications: request("/applications", { page: 0, size: 5 }),
      interviews: request("/interviews", upcomingInterviewFilters),
      pendingOffers: request("/offers", { status: "PENDING", page: 0, size: 1 }),
      pendingPlacements: request("/placements", { status: "PENDING", page: 0, size: 1 }),
      joinedCandidates: request("/placements", { status: "JOINED", page: 0, size: 1 }),
    };
  }
  if (role === "CANDIDATE") {
    return {
      candidateProfile: request("/portal/profile"),
      availableJobs: request("/portal/jobs", { page: 0, size: 1 }),
      candidateApplications: request("/portal/applications", { page: 0, size: 5 }),
      candidateInterviews: request("/portal/interviews", { page: 0, size: 5 }),
      candidateOffers: request("/portal/offers", { page: 0, size: 5 }),
      candidateNotifications: request("/portal/notifications"),
    };
  }
  return {};
}

function useDashboardData(role) {
  const [resources, setResources] = useState(EMPTY);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setResources(EMPTY);
    setLoading(true);
    const requests = requestsForRole(role);
    Promise.all(Object.entries(requests).map(async ([key, promise]) => {
      try {
        return [key, { data: await promise }];
      } catch (error) {
        return [key, { error: error.message || "Could not load this dashboard section." }];
      }
    })).then((entries) => {
      if (active) setResources(Object.fromEntries(entries));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [role]);

  return { resources, loading };
}

function MetricCards({ cards, values, loading }) {
  return (
    <div className="metric-grid">
      {cards.map(({ label, key, icon, color, suffix = "" }) => {
        const resource = values[key];
        const value = resource?.data;
        const count = ["totalCandidates", "totalCompanies", "openJobs", "totalApplications", "selectedCandidates", "joinedCandidates", "placementPercentage"]
          .includes(key) && value && !("totalElements" in value) ? value[key]
          : key === "totalCandidates" ? value?.totalCandidates
          : key === "openJobs" ? pageCount(value)
            : key === "myApplications" ? pageCount(value)
              : key === "upcomingInterviews" ? pageCount(value)
                : key === "awaitingApplications" ? pageCount(value)
                  : key === "selectedCandidates" ? value?.selectedCandidates
                    : key === "pendingOffers" ? pageCount(value)
                      : key === "pendingPlacements" ? pageCount(value)
                        : key === "joinedCandidates" || key === "joinedPlacements" ? pageCount(value)
                          : key === "availableJobs" || key === "candidateApplications" || key === "candidateInterviews" || key === "candidateOffers"
                            ? pageCount(value)
                            : key === "candidateProfile" ? (value?.profileComplete ? "Complete" : "Incomplete")
                              : undefined;
        return (
          <section className="card metric-card" key={key}>
            <div className="metric-top"><span className="metric-label">{label}</span><span className={`metric-icon ${color || ""}`}>{icon}</span></div>
            <div className="metric-value">{loading ? "…" : resource?.error ? "—" : count === undefined ? "—" : `${count}${suffix}`}</div>
            <div className="metric-note">
              {resource?.error ? "Data unavailable" : key === "totalCandidates" ? "Current system total" : "Live from your authorized workspace data"}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function QuickActions({ actions }) {
  return (
    <Card title="Quick actions">
      <div className="dashboard-actions">
        {actions.map((action) => (
          <Link className="btn btn-secondary" to={action.href} key={action.label}>{action.label} <span aria-hidden="true">→</span></Link>
        ))}
      </div>
    </Card>
  );
}

function FunnelSection({ resource }) {
  if (resource?.error) return <Card title="Recruitment funnel"><div className="error-state">{resource.error}</div></Card>;
  const rows = resource?.data?.rows || [];
  const maximum = Math.max(...rows.map((row) => Number(row.application_count || 0)), 1);
  return (
    <Card title="Recruitment funnel" action={<span className="small-note">Applications by stage</span>}>
      <div className="card-body">
        {rows.length ? <div className="funnel-chart">
          {rows.map((row) => (
            <div className="funnel-row" key={row.stage}>
              <span>{String(row.stage || "").replaceAll("_", " ")}</span>
              <div className="funnel-track"><div className="funnel-fill" style={{ width: `${Math.max(3, Number(row.application_count || 0) / maximum * 100)}%` }} /></div>
              <strong>{row.application_count}</strong>
            </div>
          ))}
        </div> : <div className="chart-empty">{resource ? "No application pipeline data yet." : "Loading funnel data…"}</div>}
      </div>
    </Card>
  );
}

function AuditSection({ resource }) {
  const items = resource?.data?.items || [];
  return (
    <Card title="Recent system activity" action={<span className="small-note">Administrator audit view</span>}>
      {resource?.error ? <div className="error-state">{resource.error}</div>
        : items.length ? <div className="card-body activity-list">
          {items.map((item) => (
            <div className="activity-item" key={item.auditLogId}>
              <span className="activity-dot" />
              <div className="activity-copy">
                <strong>{String(item.action).replaceAll("_", " ")}</strong> · {String(item.entityType).replaceAll("_", " ")}
                <br />{item.username || `User ${item.userId ?? "system"}`} · {new Date(item.createdAt).toLocaleString()}
              </div>
            </div>
          ))}
        </div> : <div className="chart-empty">{resource ? "No recent audit activity." : "Loading audit activity…"}</div>}
    </Card>
  );
}

function TaskCounts({ resources, loading, keys }) {
  return <div className="activity-list">
    {keys.map(([label, key, href]) => {
      const resource = resources[key];
      return (
        <div className="activity-item" key={key}>
          <span className="activity-dot" />
          <div className="activity-copy">
            {resource?.error ? <><strong>{label}</strong><br />{resource.error}</>
              : <><strong>{loading ? "…" : pageCount(resource?.data)} {label}</strong><br />{href ? <Link to={href}>Review items →</Link> : "Awaiting follow-up"}</>}
          </div>
        </div>
      );
    })}
  </div>;
}

function PendingRecruiters({ resource }) {
  const [requests, setRequests] = useState(resource?.data || []);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(resource?.error || "");
  const reviewLocks = useRef(new Set());

  useEffect(() => {
    setRequests(resource?.data || []);
    setError(resource?.error || "");
  }, [resource]);

  async function review(recruiterId, decision) {
    await runExclusively(reviewLocks.current, recruiterId, async () => {
      setBusyId(recruiterId);
      setError("");
      try {
        const completed = await reviewRecruiterRequest(
          api,
          recruiterId,
          decision,
          (message) => window.confirm(message),
        );
        if (completed) {
          setRequests((current) => current.filter((item) => item.userId !== recruiterId));
        }
      } catch (requestError) {
        setError(requestError.message || "Could not update this recruiter request.");
      } finally {
        setBusyId(null);
      }
    });
  }

  return (
    <Card title="Pending recruiter requests">
      {error ? <div className="error-state">{error}</div>
        : requests.length ? <div className="card-body activity-list">
          {requests.map((item) => (
            <div className="activity-item" key={item.userId}>
              <span className="activity-dot" />
              <div className="activity-copy">
                <strong>{item.fullName}</strong><br />
                {item.email} · {item.registeredAt ? new Date(item.registeredAt).toLocaleDateString() : "Registration date unavailable"}
              </div>
              <div className="dashboard-actions">
                <button className="btn btn-primary btn-small" disabled={busyId === item.userId}
                  onClick={() => review(item.userId, "APPROVE")}>Approve</button>
                <button className="btn btn-secondary btn-small" disabled={busyId === item.userId}
                  onClick={() => review(item.userId, "REJECT")}>Reject</button>
              </div>
            </div>
          ))}
        </div> : <div className="chart-empty">{resource ? "No recruiter requests are awaiting review." : "Loading recruiter requests…"}</div>}
    </Card>
  );
}

function UpcomingInterviews({ resource, title = "Upcoming interviews" }) {
  const interviews = resource?.data?.content || [];
  return (
    <Card title={title}>
      {resource?.error ? <div className="error-state">{resource.error}</div>
        : interviews.length ? <div className="card-body activity-list">
          {interviews.map((interview) => (
            <div className="activity-item" key={interview.interviewId}>
              <span className="activity-dot" />
              <div className="activity-copy">
                <strong>{interview.candidateName}</strong> · {interview.jobTitle}
                <br />{new Date(interview.scheduledAt).toLocaleString()} · {interview.interviewRound}
              </div>
              <Badge value={interview.status} />
            </div>
          ))}
        </div> : <div className="chart-empty">{resource ? "No upcoming scheduled interviews." : "Loading interviews…"}</div>}
    </Card>
  );
}

function RecruitmentActivity({ resource }) {
  const applications = resource?.data?.content || [];
  return (
    <Card title="Recruitment activity" action={<Link className="small-note" to="/applications.html">View applications →</Link>}>
      {resource?.error ? <div className="error-state">{resource.error}</div>
        : applications.length ? <div className="card-body activity-list">
          {applications.map((application) => (
            <div className="activity-item" key={application.applicationId}>
              <span className="activity-dot" />
              <div className="activity-copy">
                <strong>{application.candidateName}</strong> · {application.jobTitle}
                <br />{application.companyName} · {new Date(application.appliedAt).toLocaleDateString()}
              </div>
              <Badge value={application.status} />
            </div>
          ))}
        </div> : <div className="chart-empty">{resource ? "No applications in your recruitment activity yet." : "Loading recruitment activity…"}</div>}
    </Card>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const experience = dashboardExperienceFor(user);
  const role = user?.roles?.includes("ADMIN") ? "ADMIN"
    : user?.roles?.includes("RECRUITER") ? "RECRUITER"
      : user?.roles?.includes("CANDIDATE") ? "CANDIDATE" : "";
  const { resources, loading } = useDashboardData(role);
  const metrics = resources.metrics?.data;
  const adminValues = Object.fromEntries([
    "totalCandidates",
    "totalCompanies",
    "openJobs",
    "totalApplications",
    "selectedCandidates",
    "placementPercentage",
  ].map((key) => [key, resources.metrics]));
  adminValues.joinedPlacements = resources.joinedPlacements;
  const values = role === "ADMIN" ? adminValues : role === "CANDIDATE" ? resources : {
    ...resources,
    totalCandidates: resources.metrics,
    myApplications: resources.applications,
    upcomingInterviews: resources.interviews,
  };

  if (!experience) {
    return <div className="error-state card">Your authenticated role does not have a dashboard configuration.</div>;
  }

  return (
    <>
      <PageHeader eyebrow={`${role.replaceAll("_", " ")} dashboard`}
        title={experience.heading}
        subtitle={`Welcome, ${user?.fullName?.split(" ")[0] || "there"}. ${experience.subtitle}`} />
      {role === "ADMIN" && resources.metrics?.error && <div className="error-state card">{resources.metrics.error}</div>}
      <MetricCards cards={experience.cards} values={values} loading={loading} />

      <div className="dashboard-grid">
        {experience.sections.includes("funnel") && <FunnelSection resource={resources.funnel} />}
        {experience.sections.includes("adminTasks") && <Card title="Pending administrative tasks">
          <div className="card-body">
            <TaskCounts resources={resources} loading={loading} keys={[
              ["applications awaiting review", "awaitingApplications", "/applications.html"],
              ["upcoming interviews", "scheduledInterviews", "/interviews.html"],
              ["pending offers", "pendingOffers", "/placement-process.html"],
            ]} />
          </div>
        </Card>}
        {experience.sections.includes("audit") && <AuditSection resource={resources.audit} />}
        {role === "ADMIN" && <PendingRecruiters resource={resources.pendingRecruiters} />}
        {experience.sections.includes("upcomingInterviews") && <UpcomingInterviews resource={resources.interviews} />}
        {experience.sections.includes("recruitmentActivity") && <RecruitmentActivity resource={resources.applications} />}
        {experience.sections.includes("placementTasks") && <Card title="Applications awaiting action">
          <div className="card-body">
            <TaskCounts resources={resources} loading={loading} keys={[
              ["applications to review", "awaitingApplications", "/applications.html"],
              ["selected candidates", "selectedCandidates", "/applications.html"],
              ["offers awaiting a decision", "pendingOffers", "/placement-process.html"],
            ]} />
          </div>
        </Card>}
        {experience.sections.includes("joiningProgress") && <Card title="Joining progress">
          <div className="card-body">
            <TaskCounts resources={resources} loading={loading} keys={[
              ["pending placements", "pendingPlacements", "/placement-process.html"],
              ["joined candidates", "joinedCandidates", "/placement-process.html"],
            ]} />
          </div>
        </Card>}
        {experience.sections.includes("candidateActivity") && <>
          <Card title="Latest updates">
            {resources.candidateNotifications?.error ? <div className="error-state">{resources.candidateNotifications.error}</div>
              : (resources.candidateNotifications?.data || []).length
                ? <div className="card-body activity-list">{resources.candidateNotifications.data.map((item) => (
                  <div className="activity-item" key={`${item.type}-${item.eventId}`}>
                    <span className="activity-dot" /><div className="activity-copy">
                      <strong>{item.type.replaceAll("_", " ")}</strong><br />
                      {item.message}<br />{item.occurredAt ? new Date(item.occurredAt).toLocaleString() : ""}
                    </div>
                  </div>
                ))}</div>
                : <div className="chart-empty">{loading ? "Loading updates…" : "No new application or interview updates."}</div>}
          </Card>
          <Card title="Recent applications" action={<Link className="small-note" to="/candidate-applications.html">View all →</Link>}>
            {resources.candidateApplications?.error ? <div className="error-state">{resources.candidateApplications.error}</div>
              : (resources.candidateApplications?.data?.content || []).length
                ? <div className="card-body activity-list">{resources.candidateApplications.data.content.map((item) => (
                  <div className="activity-item" key={item.applicationId}><span className="activity-dot" /><div className="activity-copy">
                    <strong>{item.jobTitle}</strong> · {item.companyName}<br />Applied {item.appliedAt ? new Date(item.appliedAt).toLocaleDateString() : ""}
                  </div><Badge value={item.status} /></div>
                ))}</div>
                : <div className="chart-empty">{loading ? "Loading applications…" : "You have not applied to any jobs yet."}</div>}
          </Card>
          <UpcomingInterviews title="My interview activity" resource={resources.candidateInterviews} />
          <Card title="Offer updates" action={<Link className="small-note" to="/candidate-offers.html">View all →</Link>}>
            {resources.candidateOffers?.error ? <div className="error-state">{resources.candidateOffers.error}</div>
              : (resources.candidateOffers?.data?.content || []).length
                ? <div className="card-body activity-list">{resources.candidateOffers.data.content.map((item) => (
                  <div className="activity-item" key={item.offerId}><span className="activity-dot" /><div className="activity-copy">
                    <strong>{item.designation}</strong> · {item.companyName}<br />Joining date: {item.joiningDate || "Not set"}
                  </div><Badge value={item.status} /></div>
                ))}</div>
                : <div className="chart-empty">{loading ? "Loading offers…" : "No offers are available yet."}</div>}
          </Card>
        </>}
      </div>

      <QuickActions actions={experience.actions} />
    </>
  );
}
