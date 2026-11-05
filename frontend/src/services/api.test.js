import assert from "node:assert/strict";
import test from "node:test";
import { api } from "./api.js";

test("mutating API requests fetch a fresh CSRF token when login cleared its cookie", async () => {
  const originalFetch = globalThis.fetch;
  const originalDocument = globalThis.document;
  let cookie = "";
  const requests = [];
  globalThis.document = {
    get cookie() { return cookie; },
    set cookie(value) { cookie = value; },
  };
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    if (url === "/api/auth/csrf") {
      cookie = "XSRF-TOKEN=fresh-session-token";
      return new Response(JSON.stringify({
        token: "fresh-session-token",
        headerName: "X-XSRF-TOKEN",
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response(JSON.stringify({ jobId: 83, jobTitle: "Engineer" }), {
      status: 201,
      headers: { "content-type": "application/json" },
    });
  };

  try {
    const result = await api.post("/jobs", { jobTitle: "Engineer" });
    assert.deepEqual(result, { jobId: 83, jobTitle: "Engineer" });
    assert.deepEqual(requests.map(({ url }) => url), ["/api/auth/csrf", "/api/jobs"]);
    assert.equal(requests[1].options.credentials, "include");
    assert.equal(requests[1].options.headers.get("X-XSRF-TOKEN"), "fresh-session-token");
    assert.equal(requests[1].options.headers.get("Content-Type"), "application/json");
    assert.equal(requests[1].options.body, JSON.stringify({ jobTitle: "Engineer" }));
  } finally {
    globalThis.fetch = originalFetch;
    if (originalDocument === undefined) delete globalThis.document;
    else globalThis.document = originalDocument;
  }
});

test("mutating API requests use an available session CSRF cookie", async () => {
  const originalFetch = globalThis.fetch;
  const originalDocument = globalThis.document;
  const requests = [];
  globalThis.document = { cookie: "XSRF-TOKEN=existing-token" };
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return new Response(null, { status: 204 });
  };

  try {
    await api.post("/jobs", {});
    assert.deepEqual(requests.map(({ url }) => url), ["/api/jobs"]);
    assert.equal(requests[0].options.headers.get("X-XSRF-TOKEN"), "existing-token");
  } finally {
    globalThis.fetch = originalFetch;
    if (originalDocument === undefined) delete globalThis.document;
    else globalThis.document = originalDocument;
  }
});
