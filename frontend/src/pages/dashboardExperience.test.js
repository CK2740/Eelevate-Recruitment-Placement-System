import assert from "node:assert/strict";
import test from "node:test";
import { dashboardExperienceFor } from "./dashboardExperience.js";

const user = (role) => ({ roles: [role], fullName: "Authenticated User" });

test("ADMIN dashboard presents system-wide statistics, administration and audit sections", () => {
  const dashboard = dashboardExperienceFor(user("ADMIN"));

  assert.equal(dashboard.heading, "System overview");
  assert.deepEqual(dashboard.sections, ["funnel", "adminTasks", "audit"]);
  assert.deepEqual(dashboard.cards.map(({ key }) => key), [
    "totalCandidates",
    "totalCompanies",
    "openJobs",
    "totalApplications",
    "selectedCandidates",
    "joinedPlacements",
    "placementPercentage",
  ]);
  assert.ok(dashboard.actions.some(({ label }) => label === "Add company"));
  assert.ok(dashboard.actions.some(({ label }) => label === "Manage users"));
});

test("RECRUITER dashboard focuses on own recruitment activity without admin audit content", () => {
  const dashboard = dashboardExperienceFor(user("RECRUITER"));

  assert.equal(dashboard.heading, "Recruitment workspace");
  assert.deepEqual(dashboard.sections, ["upcomingInterviews", "recruitmentActivity", "placementTasks", "joiningProgress"]);
  assert.deepEqual(dashboard.cards.map(({ key }) => key), [
    "totalCandidates",
    "openJobs",
    "myApplications",
    "upcomingInterviews",
  ]);
  assert.equal(dashboard.sections.includes("audit"), false);
  assert.ok(dashboard.actions.some(({ label }) => label === "New application"));
});

test("CANDIDATE dashboard focuses on real available job opportunities", () => {
  const dashboard = dashboardExperienceFor(user("CANDIDATE"));

  assert.equal(dashboard.heading, "Candidate portal");
  assert.deepEqual(dashboard.sections, ["candidateActivity"]);
  assert.deepEqual(dashboard.cards.map(({ key }) => key), [
    "candidateProfile",
    "availableJobs",
    "candidateApplications",
    "candidateInterviews",
    "candidateOffers",
  ]);
  assert.ok(dashboard.actions.some(({ label }) => label === "Browse open jobs"));
});

test("dashboard experience derives from backend roles and fails closed for unknown roles", () => {
  assert.equal(dashboardExperienceFor({ roles: ["RECRUITER"] }).heading, "Recruitment workspace");
  assert.equal(dashboardExperienceFor({ roles: ["UNKNOWN"] }), null);
});
