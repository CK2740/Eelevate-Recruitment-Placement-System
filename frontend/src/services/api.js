const API_BASE = "/api";

function csrfToken() {
  const cookie = document.cookie
    .split("; ")
    .find((entry) => entry.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : null;
}

export function queryString(filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") params.set(key, value);
  });
  return params.toString();
}

export async function apiRequest(path, { method = "GET", body, headers = {}, responseType } = {}) {
  const upperMethod = method.toUpperCase();
  const requestHeaders = new Headers(headers);
  if (body !== undefined && !(body instanceof FormData)) {
    requestHeaders.set("Content-Type", "application/json");
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(upperMethod)) {
    let token = csrfToken();
    let headerName = "X-XSRF-TOKEN";
    if (!token && path !== "/auth/csrf") {
      const csrf = await apiRequest("/auth/csrf");
      token = csrf?.token;
      headerName = csrf?.headerName || headerName;
    }
    if (!token) throw new Error("Could not obtain a CSRF token for this request.");
    requestHeaders.set(headerName, token);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: upperMethod,
    credentials: "include",
    headers: requestHeaders,
    body: body === undefined || body instanceof FormData
      ? body
      : JSON.stringify(body),
  });

  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  if (!response.ok) {
    const payload = contentType.includes("application/json") ? await response.json() : null;
    const error = new Error(payload?.message || payload?.detail || `Request failed (${response.status})`);
    error.status = response.status;
    error.details = payload?.details || {};
    throw error;
  }
  if (responseType === "blob") return response.blob();
  return contentType.includes("application/json") ? response.json() : response.text();
}

export const api = {
  get: (path, filters) => {
    const query = queryString(filters);
    return apiRequest(query ? `${path}?${query}` : path);
  },
  post: (path, body) => apiRequest(path, { method: "POST", body }),
  put: (path, body) => apiRequest(path, { method: "PUT", body }),
  patch: (path, body) => apiRequest(path, { method: "PATCH", body }),
  delete: (path) => apiRequest(path, { method: "DELETE" }),
  download: (path, filters) => apiRequest(
    `${path}?${queryString(filters)}`,
    { responseType: "blob" },
  ),
  csrf: () => apiRequest("/auth/csrf"),
  me: () => apiRequest("/auth/me"),
  async login(email, password) {
    await api.csrf();
    return api.post("/auth/login", { email, password });
  },
  async register(fullName, email, password, confirmPassword, accountType) {
    await api.csrf();
    return api.post("/auth/register", { fullName, email, password, confirmPassword, accountType });
  },
  async logout() {
    await api.csrf();
    return apiRequest("/auth/logout", { method: "POST" });
  },
};

export const pageItems = (result) => result?.content || result?.items || [];
