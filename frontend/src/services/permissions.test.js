import assert from "node:assert/strict";
import test from "node:test";
import {
  DASHBOARD_WIDGET_PERMISSIONS,
  NAVIGATION_ITEMS,
  PAGE_PERMISSIONS,
  ROLE_PERMISSIONS,
  hasPermission,
  visibleDashboardMetrics,
  visibleNavigation,
} from "./permissions.js";

const user = (role) => ({ roles: [role] });
const allDashboardMetrics = Object.entries(DASHBOARD_WIDGET_PERMISSIONS)
  .map(([key]) => [key, key]);

test("ADMIN retains all defined management permissions and navigation", () => {
  const admin = user("ADMIN");
  const permissions = new Set(ROLE_PERMISSIONS.ADMIN);

  assert.equal(permissions.has("viewAuditLogs"), true);
  assert.equal(permissions.has("manageUsers"), true);
  assert.equal(permissions.has("manageCompanies"), true);
  assert.equal(permissions.has("manageJobs"), true);
  assert.equal(permissions.has("manageOffers"), true);
  assert.equal(permissions.has("managePlacements"), true);
  assert.deepEqual(visibleNavigation(admin).map(({ href }) => href),
    NAVIGATION_ITEMS.filter(({ permission }) => hasPermission(admin, permission)).map(({ href }) => href));
  assert.equal(visibleDashboardMetrics(admin, allDashboardMetrics).length, allDashboardMetrics.length);
  for (const [path, permission] of Object.entries(PAGE_PERMISSIONS).filter(
    ([path]) => !path.startsWith("/candidate-"),
  )) {
    assert.equal(hasPermission(admin, permission), true, `ADMIN should access ${path}`);
  }
  assert.equal(hasPermission(admin, PAGE_PERMISSIONS["/users.html"]), true);
  for (const path of [
    "/candidate-profile.html",
    "/candidate-applications.html",
    "/candidate-interviews.html",
    "/candidate-offers.html",
  ]) {
    assert.equal(hasPermission(admin, PAGE_PERMISSIONS[path]), false);
  }
});

test("RECRUITER gets recruitment and former placement workflow permissions, but not admin features", () => {
  const recruiter = user("RECRUITER");

  for (const permission of [
    "manageCandidates",
    "manageJobs",
    "createApplications",
    "manageApplicationStatuses",
    "scheduleInterviews",
    "manageInterviewFeedback",
    "manageOffers",
    "viewPlacements",
    "viewReports",
  ]) {
    assert.equal(hasPermission(recruiter, permission), true, `RECRUITER should have ${permission}`);
  }
  assert.equal(hasPermission(recruiter, "manageCompanies"), false);
  assert.equal(hasPermission(recruiter, "manageUsers"), false);
  assert.equal(hasPermission(recruiter, PAGE_PERMISSIONS["/users.html"]), false);
  for (const permission of ["managePlacements", "viewPlacementDetails"]) {
    assert.equal(hasPermission(recruiter, permission), true, `RECRUITER should have ${permission}`);
  }
  for (const permission of ["viewAuditLogs"]) {
    assert.equal(hasPermission(recruiter, permission), false, `RECRUITER must not have ${permission}`);
  }
  assert.equal(hasPermission(recruiter, PAGE_PERMISSIONS["/candidate-form.html"]), true);
  assert.equal(hasPermission(recruiter, PAGE_PERMISSIONS["/job-form.html"]), true);
  assert.equal(visibleDashboardMetrics(recruiter, allDashboardMetrics).length, allDashboardMetrics.length);
});

test("CANDIDATE can browse jobs but cannot access recruitment management pages", () => {
  const candidate = user("CANDIDATE");

  assert.equal(hasPermission(candidate, "viewDashboard"), true);
  assert.equal(hasPermission(candidate, "viewJobs"), true);
  assert.equal(hasPermission(candidate, PAGE_PERMISSIONS["/jobs.html"]), true);
  assert.equal(hasPermission(candidate, "manageJobs"), false);
  assert.equal(hasPermission(candidate, "viewApplications"), false);
  assert.equal(hasPermission(candidate, "viewCandidates"), false);
  assert.equal(
    visibleNavigation(candidate).map(({ href }) => href).join(","),
    "/dashboard.html,/jobs.html,/candidate-profile.html,/candidate-applications.html,/candidate-interviews.html,/candidate-offers.html");
  assert.equal(visibleDashboardMetrics(candidate, allDashboardMetrics).length, 1);
});

test("unknown roles fail closed and page guards follow the shared permission map", () => {
  const unknown = user("UNKNOWN_ROLE");
  assert.deepEqual(visibleNavigation(unknown), []);
  assert.deepEqual(visibleDashboardMetrics(unknown, allDashboardMetrics), []);
  for (const permission of Object.values(PAGE_PERMISSIONS)) {
    assert.equal(hasPermission(unknown, permission), false);
  }
});
