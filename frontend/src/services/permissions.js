export const ROLE_PERMISSIONS = Object.freeze({
  ADMIN: Object.freeze([
    "viewDashboard",
    "viewCandidates",
    "manageCandidates",
    "viewCompanies",
    "manageCompanies",
    "viewJobs",
    "manageJobs",
    "createApplications",
    "viewApplications",
    "manageApplicationStatuses",
    "viewInterviews",
    "scheduleInterviews",
    "manageInterviewStatuses",
    "manageInterviewFeedback",
    "manageOffers",
    "viewPlacements",
    "managePlacements",
    "viewPlacementDetails",
    "viewReports",
    "viewAuditLogs",
    "manageUsers",
  ]),
  RECRUITER: Object.freeze([
    "viewDashboard",
    "viewCandidates",
    "manageCandidates",
    "viewCompanies",
    "viewJobs",
    "manageJobs",
    "createApplications",
    "viewApplications",
    "manageApplicationStatuses",
    "viewInterviews",
    "scheduleInterviews",
    "manageInterviewStatuses",
    "manageInterviewFeedback",
    "manageOffers",
    "viewPlacements",
    "managePlacements",
    "viewPlacementDetails",
    "viewReports",
  ]),
  CANDIDATE: Object.freeze([
    "viewDashboard",
    "viewJobs",
    "viewCandidateProfile",
    "viewOwnApplications",
    "viewOwnInterviews",
    "viewOwnOffers",
  ]),
});

export const PAGE_PERMISSIONS = Object.freeze({
  "/dashboard.html": "viewDashboard",
  "/candidates.html": "viewCandidates",
  "/candidate-form.html": "manageCandidates",
  "/candidate-details.html": "viewCandidates",
  "/companies.html": "viewCompanies",
  "/jobs.html": "viewJobs",
  "/job-form.html": "manageJobs",
  "/applications.html": "viewApplications",
  "/interviews.html": "viewInterviews",
  "/placement-process.html": "viewPlacements",
  "/reports.html": "viewReports",
  "/users.html": "manageUsers",
  "/candidate-profile.html": "viewCandidateProfile",
  "/candidate-applications.html": "viewOwnApplications",
  "/candidate-interviews.html": "viewOwnInterviews",
  "/candidate-offers.html": "viewOwnOffers",
});

export const NAVIGATION_ITEMS = Object.freeze([
  { title: "Overview", href: "/dashboard.html", icon: "◫", permission: "viewDashboard" },
  { title: "Candidates", href: "/candidates.html", icon: "♙", permission: "viewCandidates" },
  { title: "Companies", href: "/companies.html", icon: "▦", permission: "viewCompanies" },
  { title: "Jobs", href: "/jobs.html", icon: "▤", permission: "viewJobs" },
  { title: "Applications", href: "/applications.html", icon: "☷", permission: "viewApplications" },
  { title: "Interviews", href: "/interviews.html", icon: "◷", permission: "viewInterviews" },
  { title: "Offers & placement", href: "/placement-process.html", icon: "◇", permission: "viewPlacements" },
  { title: "Reports", href: "/reports.html", icon: "▥", permission: "viewReports" },
  { title: "Users", href: "/users.html", icon: "♟", permission: "manageUsers" },
  { title: "My profile", href: "/candidate-profile.html", icon: "♙", permission: "viewCandidateProfile" },
  { title: "My applications", href: "/candidate-applications.html", icon: "☷", permission: "viewOwnApplications" },
  { title: "My interviews", href: "/candidate-interviews.html", icon: "◷", permission: "viewOwnInterviews" },
  { title: "My offers", href: "/candidate-offers.html", icon: "◇", permission: "viewOwnOffers" },
]);

export const DASHBOARD_WIDGET_PERMISSIONS = Object.freeze({
  totalCandidates: "viewCandidates",
  totalCompanies: "viewCompanies",
  openJobs: "viewJobs",
  totalApplications: "viewApplications",
  scheduledInterviews: "viewInterviews",
  selectedCandidates: "viewApplications",
  joinedCandidates: "viewPlacements",
  placementPercentage: "viewPlacements",
});

export function hasPermission(user, permission) {
  return (user?.roles || []).some((role) => ROLE_PERMISSIONS[role]?.includes(permission));
}

export function visibleNavigation(user) {
  return NAVIGATION_ITEMS.filter((item) => hasPermission(user, item.permission));
}

export function visibleDashboardMetrics(user, metrics) {
  return metrics.filter(([, key]) => hasPermission(user, DASHBOARD_WIDGET_PERMISSIONS[key]));
}
