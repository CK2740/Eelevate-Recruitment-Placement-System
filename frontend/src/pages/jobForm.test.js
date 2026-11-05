import assert from "node:assert/strict";
import test from "node:test";
import {
  jobRequestFromForm,
  jobSubmissionError,
  defaultJobStatus,
  loadJobCompanies,
  pageForSavedJob,
  saveJob,
} from "./jobForm.js";

function form(values) {
  return { get: (name) => values[name] ?? null };
}

const values = {
  companyId: "3",
  jobTitle: " Software Engineer ",
  technology: " Java ",
  openings: "4",
  location: " London ",
  packageAmount: "85000",
  jobDescription: "",
  skills: "Java, SQL",
  requiredQualification: "",
  applicationDeadline: "2026-12-01",
  employmentType: "FULL_TIME",
  status: "OPEN",
  minimumPercentage: "70",
  minimumPassingYear: "",
  maximumPassingYear: "2028",
  maximumBacklogs: "",
};

test("job form builds a normalized backend request with optional eligibility values", () => {
  assert.deepEqual(jobRequestFromForm(form(values)), {
    companyId: 3,
    jobTitle: "Software Engineer",
    technology: "Java",
    openings: 4,
    location: "London",
    packageAmount: 85000,
    jobDescription: null,
    skills: "Java, SQL",
    requiredQualification: null,
    applicationDeadline: "2026-12-01",
    employmentType: "FULL_TIME",
    status: "OPEN",
    eligibilityCriteria: {
      minimumPercentage: 70,
      minimumPassingYear: null,
      maximumPassingYear: 2028,
      maximumBacklogs: 0,
    },
  });
});

test("new jobs default to OPEN while edits preserve their saved status", () => {
  assert.equal(defaultJobStatus(null), "OPEN");
  assert.equal(defaultJobStatus({ status: "DRAFT" }), "DRAFT");
  assert.equal(defaultJobStatus({ status: "CLOSED" }), "CLOSED");
});

test("job save posts create requests and returns the created record", async () => {
  const calls = [];
  const saved = { jobId: 91, jobTitle: "Software Engineer" };
  const fakeApi = { post: async (...args) => { calls.push(args); return saved; } };
  const request = jobRequestFromForm(form(values));

  assert.equal(await saveJob(fakeApi, null, request), saved);
  assert.deepEqual(calls, [["/jobs", request]]);
});

test("job save updates existing jobs and exposes API validation details", async () => {
  const calls = [];
  const saved = { jobId: 12 };
  const fakeApi = { put: async (...args) => { calls.push(args); return saved; } };

  assert.equal(await saveJob(fakeApi, { jobId: 12 }, { jobTitle: "Updated" }), saved);
  assert.deepEqual(calls, [["/jobs/12", { jobTitle: "Updated" }]]);
  assert.deepEqual(jobSubmissionError({
    message: "Request validation failed",
    details: { jobTitle: "must not be blank", applicationDeadline: "must be a future date" },
  }), [
    "Request validation failed",
    "jobTitle: must not be blank",
    "applicationDeadline: must be a future date",
  ]);
  assert.deepEqual(jobSubmissionError(new Error("Access is denied")), ["Access is denied"]);
});

test("job form requests active companies and preserves the company of an existing job", async () => {
  const calls = [];
  const activeCompany = { companyId: 3, companyName: "Active Inc", status: "ACTIVE" };
  const fakeApi = {
    get: async (...args) => {
      calls.push(args);
      return { content: [activeCompany] };
    },
  };
  const existingJob = { companyId: 8, companyName: "Formerly Active Inc" };

  assert.deepEqual(await loadJobCompanies(fakeApi, existingJob), [
    activeCompany,
    { companyId: 8, companyName: "Formerly Active Inc" },
  ]);
  assert.deepEqual(calls, [["/companies", { status: "ACTIVE", size: 100 }]]);
});

test("job list selects the page containing a newly saved job", () => {
  assert.equal(pageForSavedJob(26, 10, [{ jobId: 2 }], 26), 2);
  assert.equal(pageForSavedJob(26, 10, [{ jobId: 26 }], 26), null);
});
