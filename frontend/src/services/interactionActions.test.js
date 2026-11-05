import assert from "node:assert/strict";
import test from "node:test";
import {
  downloadReportCsv,
  runExclusively,
  signOutAndClearUser,
} from "./interactionActions.js";

test("exclusive action rejects concurrent requests and releases its lock", async () => {
  const locks = new Set();
  let release;
  let calls = 0;
  const pending = new Promise((resolve) => { release = resolve; });

  const first = runExclusively(locks, "row-1", async () => {
    calls += 1;
    await pending;
  });
  assert.equal(await runExclusively(locks, "row-1", async () => { calls += 1; }), false);
  assert.equal(calls, 1);
  release();
  assert.equal(await first, true);
  assert.equal(await runExclusively(locks, "row-1", async () => { calls += 1; }), true);
  assert.equal(calls, 2);
});

test("exclusive action releases its lock after a rejected request", async () => {
  const locks = new Set();
  await assert.rejects(
    runExclusively(locks, "row-1", async () => { throw new Error("request failed"); }),
    /request failed/,
  );
  assert.equal(locks.has("row-1"), false);
});

test("CSV download fetches the filtered blob before starting the named download", async () => {
  const file = new Blob(["report"]);
  const filters = { from: "2026-01-01", companyId: "7" };
  const calls = [];
  let saved;
  await downloadReportCsv({
    download: async (...args) => {
      calls.push(args);
      return file;
    },
  }, "OPEN_JOB_AGING", filters, (...args) => { saved = args; });

  assert.deepEqual(calls, [["/reports/OPEN_JOB_AGING/csv", filters]]);
  assert.deepEqual(saved, [file, "open_job_aging.csv"]);
});

test("CSV API errors propagate without starting a download", async () => {
  let saved = false;
  await assert.rejects(
    downloadReportCsv({
      download: async () => { throw new Error("Not authorized"); },
    }, "RECRUITMENT_FUNNEL", {}, () => { saved = true; }),
    /Not authorized/,
  );
  assert.equal(saved, false);
});

test("failed sign out preserves local authentication; successful sign out clears it", async () => {
  let cleared = false;
  await assert.rejects(
    signOutAndClearUser(async () => { throw new Error("Sign out failed"); }, () => { cleared = true; }),
    /Sign out failed/,
  );
  assert.equal(cleared, false);

  await signOutAndClearUser(async () => {}, () => { cleared = true; });
  assert.equal(cleared, true);
});
