import assert from "node:assert/strict";
import test from "node:test";
import { isPendingRecruiter, reviewRecruiterRequest } from "./userManagement.js";

test("approval actions are only available for pending Recruiter accounts", () => {
  assert.equal(isPendingRecruiter({
    accountStatus: "PENDING_APPROVAL",
    roles: ["RECRUITER"],
  }), true);
  assert.equal(isPendingRecruiter({
    accountStatus: "APPROVED",
    roles: ["RECRUITER"],
  }), false);
  assert.equal(isPendingRecruiter({
    accountStatus: "PENDING_APPROVAL",
    roles: ["CANDIDATE"],
  }), false);
});

test("recruiter approval/rejection uses the protected existing endpoint after confirmation", async () => {
  const calls = [];
  const fakeApi = { post: async (...args) => calls.push(args) };
  const confirmedMessages = [];

  assert.equal(await reviewRecruiterRequest(
    fakeApi, 42, "APPROVE", (message) => {
      confirmedMessages.push(message);
      return true;
    }), true);
  assert.deepEqual(calls, [[
    "/auth/admin/recruiters/42/approval",
    { decision: "APPROVE" },
  ]]);
  assert.match(confirmedMessages[0], /approve/);

  calls.length = 0;
  assert.equal(await reviewRecruiterRequest(
    fakeApi, 43, "REJECT", (message) => {
      confirmedMessages.push(message);
      return true;
    }), true);
  assert.deepEqual(calls, [[
    "/auth/admin/recruiters/43/approval",
    { decision: "REJECT" },
  ]]);
  assert.match(confirmedMessages[1], /reject/);
});

test("cancelled recruiter decision does not call the approval endpoint", async () => {
  const calls = [];
  const fakeApi = { post: async (...args) => calls.push(args) };

  assert.equal(await reviewRecruiterRequest(fakeApi, 42, "APPROVE", () => false), false);
  assert.deepEqual(calls, []);
});
