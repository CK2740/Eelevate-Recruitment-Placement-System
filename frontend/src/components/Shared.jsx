import { useState } from "react";

export function Button({ children, variant = "primary", small = false, ...props }) {
  return (
    <button className={`btn btn-${variant}${small ? " btn-small" : ""}`} {...props}>
      {children}
    </button>
  );
}

export function PageHeader({ eyebrow, title, subtitle, action }) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function Card({ title, action, children, className = "" }) {
  return (
    <section className={`card ${className}`}>
      {(title || action) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

export function Field({ label, name, className = "", hint, as, children, ...props }) {
  return (
    <div className={`field ${className}`}>
      <label htmlFor={name}>{label}</label>
      {as === "textarea" ? (
        <textarea id={name} name={name} {...props} />
      ) : as === "select" ? (
        <select id={name} name={name} {...props}>{children}</select>
      ) : (
        <input id={name} name={name} {...props} />
      )}
      {hint && <span className="field-hint">{hint}</span>}
    </div>
  );
}

export function Badge({ children, value }) {
  const label = value ?? children ?? "—";
  const normalized = String(label).toLowerCase();
  const kind = /joined|accepted|selected|completed|active|open|pass/.test(normalized)
    ? ""
    : /rejected|cancelled|expired|not joined|closed|fail/.test(normalized)
      ? "red"
      : /pending|scheduled|applied|shortlisted|interview/.test(normalized)
        ? "orange"
        : "gray";
  return <span className={`badge ${kind}`}>{label.replaceAll("_", " ")}</span>;
}

export function DataTable({ columns, rows, loading, error, empty = "No records found", rowKey }) {
  return (
    <div className="table-wrap">
      {loading ? <div className="loading-state">Loading records…</div>
        : error ? <div className="error-state">{error}</div>
          : rows.length === 0 ? <div className="empty-state"><div className="empty-icon">⌕</div>{empty}</div>
            : (
              <table>
                <thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead>
                <tbody>
                  {rows.map((row, index) => (
                    <tr key={rowKey ? rowKey(row) : row.id ?? row.candidateId ?? row.companyId ?? row.jobId ?? row.applicationId ?? row.interviewId ?? row.offerId ?? row.placementId ?? index}>
                      {columns.map((column) => <td key={column.key}>{column.render ? column.render(row) : (row[column.key] ?? "—")}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, size, onPageChange }) {
  const safeTotal = Math.max(totalPages || 0, 1);
  return (
    <div className="pagination">
      <span>{totalElements || 0} records · Page {(page || 0) + 1} of {safeTotal}</span>
      <div className="pagination-actions">
        <button type="button" aria-label="Previous page" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>‹</button>
        <button type="button" aria-label="Next page" disabled={page + 1 >= safeTotal || (totalElements || 0) <= size} onClick={() => onPageChange(page + 1)}>›</button>
      </div>
    </div>
  );
}

export function Modal({ title, onClose, children }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head"><h2>{title}</h2><button className="icon-button" type="button" onClick={onClose} aria-label="Close dialog">×</button></div>
        {children}
      </section>
    </div>
  );
}

export function ToastRegion({ items, dismiss }) {
  return (
    <div className="toast-region" aria-live="polite">
      {items.map((item) => <button key={item.id} className={`toast${item.error ? " error" : ""}`} onClick={() => dismiss(item.id)}>{item.message}</button>)}
    </div>
  );
}

export function useToast() {
  const [items, setItems] = useState([]);
  const push = (message, error = false) => {
    const id = Date.now() + Math.random();
    setItems((current) => [...current, { id, message, error }]);
    window.setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), 4500);
  };
  const dismiss = (id) => setItems((current) => current.filter((item) => item.id !== id));
  return { items, push, dismiss };
}

export function StatusSelect({ value, options, onChange, name = "status" }) {
  return <select name={name} value={value} onChange={(event) => onChange(event.target.value)}>{options.map((option) => <option key={option} value={option}>{option.replaceAll("_", " ")}</option>)}</select>;
}

export function LoadingPage({ text = "Loading your workspace…" }) {
  return <main className="loading-screen">{text}</main>;
}
