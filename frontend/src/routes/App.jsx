import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import Layout from "../components/Layout.jsx";
import { LoadingPage } from "../components/Shared.jsx";
import { useAuth } from "../services/AuthContext.jsx";
import LoginPage from "../pages/LoginPage.jsx";
import DashboardPage from "../pages/DashboardPage.jsx";
import { CandidateDetailsPage, CandidateFormPage, CandidatesPage } from "../pages/CandidatePages.jsx";
import { CompaniesPage, JobFormPage, JobsPage } from "../pages/CompanyJobsPages.jsx";
import { ApplicationsPage, InterviewsPage, PlacementProcessPage } from "../pages/RecruitmentPages.jsx";
import ReportsPage from "../pages/ReportsPage.jsx";

function RequireAuth({ children, roles }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <LoadingPage />;
  if (!user) return <Navigate to="/login.html" replace state={{ from: location }} />;
  if (roles && !roles.some((role) => user.roles?.includes(role))) {
    return <Navigate to="/dashboard.html" replace />;
  }
  return children;
}

function ProtectedLayout() {
  return <RequireAuth><Layout /></RequireAuth>;
}

const managers = ["ADMIN", "RECRUITER"];
const viewers = ["ADMIN", "RECRUITER", "PLACEMENT_OFFICER"];

export default function App() {
  return (
    <Routes>
      <Route path="/login.html" element={<LoginPage />} />
      <Route element={<ProtectedLayout />}>
        <Route index element={<Navigate to="/dashboard.html" replace />} />
        <Route path="/dashboard.html" element={<RequireAuth roles={viewers}><DashboardPage /></RequireAuth>} />
        <Route path="/candidates.html" element={<RequireAuth roles={viewers}><CandidatesPage /></RequireAuth>} />
        <Route path="/candidate-form.html" element={<RequireAuth roles={managers}><CandidateFormPage /></RequireAuth>} />
        <Route path="/candidate-details.html" element={<RequireAuth roles={viewers}><CandidateDetailsPage /></RequireAuth>} />
        <Route path="/companies.html" element={<RequireAuth roles={viewers}><CompaniesPage /></RequireAuth>} />
        <Route path="/jobs.html" element={<RequireAuth roles={viewers}><JobsPage /></RequireAuth>} />
        <Route path="/job-form.html" element={<RequireAuth roles={managers}><JobFormPage /></RequireAuth>} />
        <Route path="/applications.html" element={<RequireAuth roles={viewers}><ApplicationsPage /></RequireAuth>} />
        <Route path="/interviews.html" element={<RequireAuth roles={viewers}><InterviewsPage /></RequireAuth>} />
        <Route path="/placement-process.html" element={<RequireAuth roles={viewers}><PlacementProcessPage /></RequireAuth>} />
        <Route path="/reports.html" element={<RequireAuth roles={viewers}><ReportsPage /></RequireAuth>} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard.html" replace />} />
    </Routes>
  );
}
