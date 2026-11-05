import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../services/AuthContext.jsx";
import { Button } from "./Shared.jsx";

const links = [
  { title: "Overview", href: "/dashboard.html", icon: "◫", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Candidates", href: "/candidates.html", icon: "♙", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Companies", href: "/companies.html", icon: "▦", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Jobs", href: "/jobs.html", icon: "▤", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Applications", href: "/applications.html", icon: "☷", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Interviews", href: "/interviews.html", icon: "◷", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Offers & placement", href: "/placement-process.html", icon: "◇", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
  { title: "Reports", href: "/reports.html", icon: "▥", roles: ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"] },
];

const titles = Object.fromEntries(links.map((link) => [link.href, link.title]));

export default function Layout() {
  const { user, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const name = user?.fullName || user?.email || "Team member";
  const initials = name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
  const role = user?.roles?.[0]?.replaceAll("_", " ") || "Member";

  async function logout() {
    await signOut();
    window.location.hash = "/login.html";
  }

  return (
    <div className="app-shell">
      {open && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} />}
      <aside className={`sidebar${open ? " open" : ""}`}>
        <a className="brand" href="#/dashboard.html"><span className="brand-mark">E</span><span>elevate</span></a>
        <div className="nav-label">Workspace</div>
        <nav className="nav-list" aria-label="Main navigation">
          {links.filter((link) => link.roles.some((allowed) => user?.roles?.includes(allowed))).map((link) => (
            <NavLink key={link.href} to={link.href} onClick={() => setOpen(false)} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
              <span className="nav-icon">{link.icon}</span>{link.title}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">Recruitment &amp; placement<br />Management workspace</div>
      </aside>
      <div className="main-area">
        <header className="topbar">
          <div className="top-left">
            <button type="button" className="icon-button menu-toggle" aria-label="Open navigation" onClick={() => setOpen(true)}>☰</button>
            <span className="breadcrumb">Workspace <span aria-hidden="true">/</span> {titles[location.pathname] || "Overview"}</span>
          </div>
          <div className="top-actions">
            <div className="user-chip">
              <div className="avatar">{initials}</div>
              <div className="user-meta"><span className="user-name">{name}</span><span className="user-role">{role}</span></div>
            </div>
            <Button variant="secondary" small onClick={logout}>Sign out</Button>
          </div>
        </header>
        <main className="page-content"><Outlet /></main>
      </div>
    </div>
  );
}
