import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import Layout from "../components/Layout.jsx";
import { LoadingPage } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";
import LoginPage from "../pages/LoginPage.jsx";
import RegisterPage from "../pages/RegisterPage.jsx";
import RegistrationPendingPage from "../pages/RegistrationPendingPage.jsx";
import DashboardPage from "../pages/DashboardPage.jsx";
import { CandidateDetailsPage, CandidateFormPage, CandidatesPage } from "../pages/CandidatePages.jsx";
import { CompaniesPage, JobFormPage, JobsPage } from "../pages/CompanyJobsPages.jsx";
import { ApplicationsPage, InterviewsPage, PlacementProcessPage } from "../pages/RecruitmentPages.jsx";
import ReportsPage from "../pages/ReportsPage.jsx";
import UsersPage from "../pages/UsersPage.jsx";
import {
  CandidateApplicationsPage,
  CandidateInterviewsPage,
  CandidateOffersPage,
  CandidatePortalJobsPage,
  CandidateProfilePage,
} from "../pages/CandidatePortalPages.jsx";
import { PAGE_PERMISSIONS } from "../services/permissions.js";

function RequireAuth({ children, permission }) {
  const { user, loading, can } = useAuth();
  const location = useLocation();
  if (loading) return <LoadingPage />;
  if (!user) return <Navigate to="/login.html" replace state={{ from: location }} />;
  if (permission && !can(permission)) {
    return <Navigate to="/access-denied.html" replace state={{ from: location }} />;
  }
  return children;
}

function ProtectedLayout() {
  return <RequireAuth><Layout /></RequireAuth>;
}

function JobsRoute() {
  const { user } = useAuth();
  return user?.roles?.includes("CANDIDATE") ? <CandidatePortalJobsPage /> : <JobsPage />;
}

function AccessDeniedPage() {
  return (
    <section className="card access-denied">
      <div className="eyebrow">Access restricted</div>
      <h1>You don’t have permission to view this page.</h1>
      <p>Your account role does not grant access to this page or action.</p>
      <a className="btn btn-primary" href="#/dashboard.html">Return to dashboard</a>
    </section>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login.html" element={<LoginPage />} />
      <Route path="/register.html" element={<RegisterPage />} />
      <Route path="/registration-pending.html" element={<RegistrationPendingPage />} />
      <Route element={<ProtectedLayout />}>
        <Route index element={<Navigate to="/dashboard.html" replace />} />
        <Route path="/access-denied.html" element={<AccessDeniedPage />} />
        <Route path="/dashboard.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/dashboard.html"]}><DashboardPage /></RequireAuth>} />
        <Route path="/candidates.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidates.html"]}><CandidatesPage /></RequireAuth>} />
        <Route path="/candidate-form.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidate-form.html"]}><CandidateFormPage /></RequireAuth>} />
        <Route path="/candidate-details.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidate-details.html"]}><CandidateDetailsPage /></RequireAuth>} />
        <Route path="/companies.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/companies.html"]}><CompaniesPage /></RequireAuth>} />
        <Route path="/jobs.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/jobs.html"]}><JobsRoute /></RequireAuth>} />
        <Route path="/job-form.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/job-form.html"]}><JobFormPage /></RequireAuth>} />
        <Route path="/applications.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/applications.html"]}><ApplicationsPage /></RequireAuth>} />
        <Route path="/interviews.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/interviews.html"]}><InterviewsPage /></RequireAuth>} />
        <Route path="/placement-process.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/placement-process.html"]}><PlacementProcessPage /></RequireAuth>} />
        <Route path="/reports.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/reports.html"]}><ReportsPage /></RequireAuth>} />
        <Route path="/users.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/users.html"]}><UsersPage /></RequireAuth>} />
        <Route path="/candidate-profile.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidate-profile.html"]}><CandidateProfilePage /></RequireAuth>} />
        <Route path="/candidate-applications.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidate-applications.html"]}><CandidateApplicationsPage /></RequireAuth>} />
        <Route path="/candidate-interviews.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidate-interviews.html"]}><CandidateInterviewsPage /></RequireAuth>} />
        <Route path="/candidate-offers.html" element={<RequireAuth permission={PAGE_PERMISSIONS["/candidate-offers.html"]}><CandidateOffersPage /></RequireAuth>} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard.html" replace />} />
    </Routes>
  );
}
