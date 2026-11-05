import { useEffect, useState } from "react";
import { api } from "../services/api.js";
import { Badge, Button, Card, DataTable, Field, PageHeader, useToast, ToastRegion } from "../components/Shared.jsx";

const REPORTS = [
  ["RECRUITMENT_FUNNEL", "Recruitment funnel"],
  ["COMPANY_WISE_PLACEMENTS", "Company-wise placements"],
  ["TECHNOLOGY_WISE_PLACEMENTS", "Technology-wise placements"],
  ["RECRUITER_PERFORMANCE", "Recruiter performance"],
  ["PENDING_INTERVIEWS", "Pending interviews"],
  ["OPEN_JOB_AGING", "Open job aging"],
];

export default function ReportsPage() {
  const [type, setType] = useState(REPORTS[0][0]);
  const [filters, setFilters] = useState({
    from: "", to: "", companyId: "", technology: "", recruiterId: "", passingYear: "",
  });
  const [companies, setCompanies] = useState([]);
  const [companyError, setCompanyError] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [requested, setRequested] = useState(false);
  const toast = useToast();

  useEffect(() => {
    api.get("/companies", { size: 100 })
      .then((response) => setCompanies(response.content || []))
      .catch((requestError) => setCompanyError(requestError.message));
  }, []);

  async function runReport(event) {
    event?.preventDefault();
    setLoading(true); setError(""); setRequested(true);
    try { setResult(await api.get(`/reports/${type}`, filters)); }
    catch (requestError) { setError(requestError.message); setResult(null); }
    finally { setLoading(false); }
  }

  async function downloadCsv() {
    try {
      const blob = await api.download(`/reports/${type}/csv`, filters);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${type.toLowerCase()}.csv`;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (requestError) { toast.push(requestError.message, true); }
  }

  const columns = result?.rows?.length
    ? Object.keys(result.rows[0]).map((key) => ({
      key,
      label: key.replaceAll("_", " "),
      render: (row) => {
        const value = row[key];
        if (key === "status" || key === "stage" || key === "joining_status") return <Badge value={value} />;
        if (value === null || value === undefined) return "—";
        if (typeof value === "number" && key.includes("package")) return value.toLocaleString();
        return String(value).replaceAll("_", " ");
      },
    }))
    : [];
  const funnelKey = result?.rows?.length
    ? Object.keys(result.rows[0]).find((key) => ["stage", "status"].includes(key))
    : null;
  const funnelValueKey = result?.rows?.length
    ? Object.keys(result.rows[0]).find((key) => key.includes("count"))
    : null;
  const maxFunnel = funnelValueKey
    ? Math.max(...result.rows.map((row) => Number(row[funnelValueKey] || 0)), 1)
    : 1;

  return <>
    <PageHeader eyebrow="Insights & analytics" title="Reports" subtitle="Explore recruitment outcomes and export filtered data." />
    <Card title="Build a report">
      <form className="report-bar" onSubmit={runReport}>
        <Field label="Report" name="reportType" as="select" value={type} onChange={(event) => { setType(event.target.value); setResult(null); setRequested(false); }}>
          {REPORTS.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
        </Field>
        <Field label="From date" name="from" type="date" value={filters.from} onChange={(event) => setFilters({ ...filters, from: event.target.value })} />
        <Field label="To date" name="to" type="date" value={filters.to} onChange={(event) => setFilters({ ...filters, to: event.target.value })} />
        <Field label="Company" name="companyId" as="select" value={filters.companyId} onChange={(event) => setFilters({ ...filters, companyId: event.target.value })}>
          <option value="">All companies</option>{companies.map((company) => <option key={company.companyId} value={company.companyId}>{company.companyName}</option>)}
        </Field>
        <Field label="Technology" name="technology" value={filters.technology} onChange={(event) => setFilters({ ...filters, technology: event.target.value })} placeholder="Any technology" />
        <Field label="Recruiter ID" name="recruiterId" type="number" min="1" value={filters.recruiterId} onChange={(event) => setFilters({ ...filters, recruiterId: event.target.value })} />
        <Field label="Passing year" name="passingYear" type="number" min="1901" max="2155" value={filters.passingYear} onChange={(event) => setFilters({ ...filters, passingYear: event.target.value })} />
        <Button type="submit" disabled={loading}>{loading ? "Generating…" : "Run report"}</Button>
      </form>
      {companyError && <p className="field-hint" style={{ padding: "0 16px 12px" }}>Company filter unavailable: {companyError}</p>}
    </Card>
    <Card title={REPORTS.find(([value]) => value === type)?.[1] || "Report"} action={result && <Button variant="secondary" onClick={downloadCsv}>↓ Export CSV</Button>}>
      {error ? <div className="error-state">{error}</div>
        : loading ? <div className="loading-state">Generating report…</div>
          : result?.rows?.length ? <>
            {type === "RECRUITMENT_FUNNEL" && funnelKey && funnelValueKey && <div className="funnel-chart report-funnel">
              {result.rows.map((row) => <div className="funnel-row" key={row[funnelKey]}>
                <span>{String(row[funnelKey]).replaceAll("_", " ")}</span>
                <div className="funnel-track"><div className="funnel-fill" style={{ width: `${Math.max(3, Number(row[funnelValueKey]) / maxFunnel * 100)}%` }} /></div>
                <strong>{row[funnelValueKey]}</strong>
              </div>)}
            </div>}
            <DataTable rows={result.rows} columns={columns} />
            <div className="report-footnote">Generated {new Date(result.generatedAt).toLocaleString()} · {result.rows.length} rows</div>
          </> : requested ? <div className="empty-state"><div className="empty-icon">▥</div>No records match the selected report filters.</div>
            : <div className="empty-state"><div className="empty-icon">▥</div>Select filters and run a report to see results.</div>}
    </Card>
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}
