import { useEffect, useState } from "react";
import { api } from "../services/api.js";
import { Badge, Button, Card, DataTable, Field, Modal, PageHeader, Pagination, ToastRegion, useToast } from "../components/Shared.jsx";
import { isPendingRecruiter, reviewRecruiterRequest } from "./userManagement.js";

const PAGE_SIZE = 10;
const ROLES = ["ADMIN", "RECRUITER", "CANDIDATE"];

function CreateUserModal({ onClose, onCreated }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      await api.post("/auth/users", {
        email: form.get("email").trim(),
        fullName: form.get("fullName").trim(),
        password: form.get("password"),
        role: form.get("role"),
      });
      onCreated();
      onClose();
    } catch (requestError) {
      setError(requestError.message || "Could not create the user.");
    } finally {
      setBusy(false);
    }
  }

  return <Modal title="Create user" onClose={onClose}>
    <form className="card-body" onSubmit={submit}>
      {error && <div className="error-state">{error}</div>}
      <div className="form-grid">
        <Field label="Full name" name="fullName" required maxLength={160} />
        <Field label="Email" name="email" type="email" required maxLength={254} />
        <Field label="Temporary password" name="password" type="password" required minLength={12} maxLength={72} />
        <Field label="Role" name="role" as="select" defaultValue="RECRUITER" required>
          {ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
        </Field>
      </div>
      <div className="form-actions">
        <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
        <Button type="submit" disabled={busy}>{busy ? "Creating…" : "Create user"}</Button>
      </div>
    </form>
  </Modal>;
}

export default function UsersPage() {
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [roleChanges, setRoleChanges] = useState({});
  const [busyUser, setBusyUser] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const toast = useToast();

  async function load() {
    setLoading(true);
    setError("");
    try {
      setData(await api.get("/auth/users", { page, size: PAGE_SIZE }));
    } catch (requestError) {
      setError(requestError.message || "Could not load users.");
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, [page]);

  async function saveRole(user) {
    const role = roleChanges[user.userId];
    if (!role || (user.roles?.length === 1 && user.roles[0] === role)) return;
    setBusyUser(user.userId);
    try {
      await api.patch(`/auth/users/${user.userId}/role`, { role });
      toast.push("User role updated.");
      setRoleChanges((current) => {
        const next = { ...current };
        delete next[user.userId];
        return next;
      });
      await load();
    } catch (requestError) {
      toast.push(requestError.message || "Could not update this user's role.", true);
    } finally {
      setBusyUser(null);
    }
  }

  async function reviewRecruiter(user, decision) {
    setBusyUser(user.userId);
    try {
      const completed = await reviewRecruiterRequest(
        api,
        user.userId,
        decision,
        (message) => window.confirm(message),
      );
      if (!completed) return;
      toast.push(decision === "APPROVE"
        ? "Recruiter approved and account activated."
        : "Recruiter registration rejected.");
      await load();
    } catch (requestError) {
      toast.push(requestError.message || "Could not review this recruiter request.", true);
    } finally {
      setBusyUser(null);
    }
  }

  return <>
    <PageHeader eyebrow="Administration" title="User management"
      subtitle="Review account status and assign one of the three supported roles."
      action={<Button onClick={() => setCreateOpen(true)}>＋ Create user</Button>} />
    <Card>
      <DataTable loading={loading} error={error} rows={data.content || []} empty="No user accounts were found." columns={[
        { key: "fullName", label: "User", render: (user) => <div className="primary-cell"><strong>{user.fullName}</strong><span>{user.email}</span></div> },
        { key: "roles", label: "Role", render: (user) => <div className="table-actions">
          <select aria-label={`Role for ${user.email}`}
            value={roleChanges[user.userId] ?? (user.roles?.length === 1 ? user.roles[0] : "")}
            disabled={user.accountStatus === "PENDING_APPROVAL"}
            onChange={(event) => setRoleChanges((current) => ({ ...current, [user.userId]: event.target.value }))}>
            {user.roles?.length !== 1 && <option value="">Select role</option>}
            {ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
          <Button variant="secondary" small disabled={user.accountStatus === "PENDING_APPROVAL"
            || busyUser === user.userId
            || roleChanges[user.userId] === undefined
            || (user.roles?.length === 1 && user.roles[0] === roleChanges[user.userId])}
            onClick={() => saveRole(user)}>{busyUser === user.userId ? "Saving…" : "Save role"}</Button>
        </div> },
        { key: "accountStatus", label: "Account status", render: (user) => <Badge value={user.accountStatus} /> },
        { key: "active", label: "Active", render: (user) => user.active ? "Yes" : "No" },
        { key: "createdAt", label: "Registered", render: (user) => user.createdAt ? new Date(user.createdAt).toLocaleDateString() : "—" },
        { key: "actions", label: "Actions", render: (user) => isPendingRecruiter(user) && <div className="table-actions">
          <Button small disabled={busyUser === user.userId}
            onClick={() => reviewRecruiter(user, "APPROVE")}>
            {busyUser === user.userId ? "Processing…" : "Approve"}
          </Button>
          <Button variant="danger" small disabled={busyUser === user.userId}
            onClick={() => reviewRecruiter(user, "REJECT")}>
            Reject
          </Button>
        </div> },
      ]} />
      <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements}
        size={PAGE_SIZE} onPageChange={setPage} />
    </Card>
    {createOpen && <CreateUserModal onClose={() => setCreateOpen(false)} onCreated={() => {
      toast.push("User created.");
      load();
    }} />}
    <ToastRegion items={toast.items} dismiss={toast.dismiss} />
  </>;
}
