import assert from "node:assert/strict";
import test from "node:test";
import {
  applicationStatusRequiresReason,
  nextApplicationStatuses,
} from "./applicationStatus.js";

test("application status choices follow the backend workflow transitions", () => {
  assert.deepEqual(nextApplicationStatuses("APPLIED"), ["SHORTLISTED", "REJECTED"]);
  assert.deepEqual(nextApplicationStatuses("SHORTLISTED"), ["INTERVIEW", "REJECTED"]);
  assert.deepEqual(nextApplicationStatuses("INTERVIEW"), ["SELECTED", "REJECTED"]);
  assert.deepEqual(nextApplicationStatuses("SELECTED"), ["OFFERED", "REJECTED"]);
  assert.deepEqual(nextApplicationStatuses("OFFERED"), ["JOINED", "REJECTED"]);
  assert.deepEqual(nextApplicationStatuses("JOINED"), []);
  assert.deepEqual(nextApplicationStatuses("REJECTED"), []);
  assert.deepEqual(nextApplicationStatuses("UNKNOWN"), []);
});

test("shortlisting and rejection require a reason", () => {
  assert.equal(applicationStatusRequiresReason("SHORTLISTED"), true);
  assert.equal(applicationStatusRequiresReason("REJECTED"), true);
  assert.equal(applicationStatusRequiresReason("INTERVIEW"), false);
  assert.equal(applicationStatusRequiresReason("SELECTED"), false);
});
