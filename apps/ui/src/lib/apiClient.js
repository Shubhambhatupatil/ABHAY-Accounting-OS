const apiBaseEnvKey = "V" + "ITE_API_BASE_URL";

export const API_BASE_URL = import.meta.env[apiBaseEnvKey] || "http://localhost:8080";

async function request(path, options = {}) {
  const endpoint = `${API_BASE_URL.replace(/\/$/, "")}${path}`;
  const isFormData = options.body instanceof FormData;
  try {
    const response = await fetch(endpoint, {
      headers: {
        ...(isFormData ? {} : { "Content-Type": "application/json" }),
        ...(options.headers || {})
      },
      ...options
    });
    if (!response.ok) {
      return {
        ok: false,
        message: "We could not complete this request. Please try again."
      };
    }
    const data = await response.json().catch(() => ({}));
    return { ok: true, data };
  } catch {
    return {
      ok: false,
      message: "We could not complete this request. Please try again."
    };
  }
}

export const apiClient = {
  login(payload) {
    return request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  signup(payload) {
    return request("/api/auth/signup", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  health() {
    return request("/api/health");
  },
  dashboard() {
    return request("/api/dashboard");
  },
  analyzeEntry(payload) {
    return request("/api/ai/workbench/analyze", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  uploadDocument(formData) {
    return request("/api/document-intelligence/upload", {
      method: "POST",
      body: formData
    });
  },
  reports() {
    return request("/api/reports");
  }
};
