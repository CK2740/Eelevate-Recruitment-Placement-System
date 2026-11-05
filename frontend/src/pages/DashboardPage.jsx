import { useEffect, useState } from "react";
import { api } from "../services/api.js";
import { Badge, Card, PageHeader } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";

const metricCards = [
  ["Total candidates", "totalCandidates", "♙", ""],
  ["Companies", "totalCompanies", "▦", "blue"],
  ["Open jobs", "openJobs", "▤", "orange"],
  ["Applications", "totalApplications", "☷", "purple"],
  ["Scheduled interviews", "scheduledInterviews", "◷", ""],
  ["Selected candidates", "selectedCandidates", "✦", "blue"],
  ["Joined candidates", "joinedCandidates", "✓", ""],
  ["Placement percentage", "placementPercentage", "%", "orange", true],
];

export default function DashboardPage() {
  const { user } = useAuth();
  const [metrics, setMetrics] = useState(null);
  const [funnel, setFunnel] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([
      api.get("/dashboard/metrics"),
      api.get("/reports/RECRUITMENT_FUNNEL"),
    ]).then(([dashboard, report]) => {
      if (!active) return;
      setMetrics(dashboard);
      setFunnel(report.rows || []);
    }).catch((requestError) => {
      if (active) setError(requestError.message);
    });
    return () => { active = false; };
  }, []);

  const maxFunnel = Math.max(...funnel.map((row) => Number(row.application_count || 0)), 1);
  const formatStage = (stage) => String(stage || "").replaceAll("_", " ");

  return (
    <>
      <PageHeader eyebrow={`${user?.roles?.[0]?.replaceAll("_", " ") || "Team"} workspace`}
        title={`Good to see you, ${user?.fullName?.split(" ")[0] || "there"}`}
        subtitle={user?.roles?.includes("PLACEMENT_OFFICER")
          ? "Track candidate progress, interviews and successful placements."
          : user?.roles?.includes("RECRUITER")
            ? "Keep hiring activity, open roles and candidate progress in view."
            : "Here’s your recruitment pipeline at a glance."} />
      {error && !metrics ? <div className="error-state card">{error}</div> : null}
      <div className="metric-grid">
        {metricCards.map(([label, key, icon, color, percent]) => (
          <section className="card metric-card" key={key}>
            <div className="metric-top"><span className="metric-label">{label}</span><span className={`metric-icon ${color}`}>{icon}</span></div>
            <div className="metric-value">{metrics ? `${metrics[key] ?? 0}${percent ? "%" : ""}` : "—"}</div>
            <div className="metric-note">Current system total</div>
          </section>
        ))}
      </div>
      <div className="dashboard-grid">
        <Card title="Recruitment funnel" action={<span className="small-note">Applications by stage</span>}>
          <div className="card-body">
            {funnel.length ? <div className="funnel-chart">
              {funnel.map((row) => (
                <div className="funnel-row" key={row.stage}>
                  <span>{formatStage(row.stage)}</span>
                  <div className="funnel-track"><div className="funnel-fill" style={{ width: `${Math.max(3, Number(row.application_count) / maxFunnel * 100)}%` }} /></div>
                  <strong>{row.application_count}</strong>
                </div>
              ))}
            </div> : <div className="chart-empty">{error || "No application pipeline data yet."}</div>}
          </div>
        </Card>
        <Card title="Pipeline snapshot">
          <div className="card-body">
            {metrics ? <div className="activity-list">
              <div className="activity-item"><span className="activity-dot" /><div className="activity-copy"><strong>{metrics.openJobs} open roles</strong><br />ready for candidate applications</div></div>
              <div className="activity-item"><span className="activity-dot" /><div className="activity-copy"><strong>{metrics.scheduledInterviews} interviews</strong><br />currently on the calendar</div></div>
              <div className="activity-item"><span className="activity-dot" /><div className="activity-copy"><strong>{metrics.joinedCandidates} joined candidates</strong><br />{metrics.placementPercentage}% overall placement rate</div></div>
              <div className="activity-item"><Badge value={user?.roles?.[0] || "MEMBER"} /><div className="activity-copy">Your workspace is tailored to your role.</div></div>
            </div> : <div className="loading-state">Loading your metrics…</div>}
          </div>
        </Card>
      </div>
    </>
  );
}
