import type { Asset, LoginResponse, Note } from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? "/api";

function endpoint(path: string): string {
  const clean = path.startsWith("/api/") ? path.slice(4) : path;
  return `${API_BASE}${clean.startsWith("/") ? clean : `/${clean}`}`;
}

async function request<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (!(init.body instanceof FormData) && init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(endpoint(path), { ...init, headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function assetUrl(asset: Asset, token: string): string {
  const separator = asset.url.includes("?") ? "&" : "?";
  return `${endpoint(asset.url)}${separator}token=${encodeURIComponent(token)}`;
}

export function extractDouyinUrl(text: string): string | null {
  const match = text.match(/https?:\/\/[^\s，。；;:'"<>]+/i);
  return match ? match[0].replace(/[:/]+$/, "") : null;
}

export const api = {
  login(password: string, deviceName = "web") {
    return request<LoginResponse>("/auth/login", null, {
      method: "POST",
      body: JSON.stringify({ password, device_name: deviceName })
    });
  },
  listNotes(token: string, params: { query?: string; type?: string; tag?: string; from?: string; to?: string }) {
    const query = new URLSearchParams();
    if (params.query) query.set("query", params.query);
    if (params.type) query.set("type", params.type);
    if (params.tag) query.set("tag", params.tag);
    if (params.from) query.set("from", params.from);
    if (params.to) query.set("to", params.to);
    return request<{ items: Note[] }>(`/notes?${query.toString()}`, token);
  },
  getNote(token: string, id: string) {
    return request<Note>(`/notes/${id}`, token);
  },
  random(token: string) {
    return request<Note>("/notes/random", token);
  },
  updateNote(token: string, id: string, payload: { title?: string; remark?: string; tags?: string[] }) {
    return request<Note>(`/notes/${id}`, token, {
      method: "PATCH",
      body: JSON.stringify(payload)
    });
  },
  deleteNote(token: string, id: string) {
    return request<{ deleted: boolean }>(`/notes/${id}`, token, {
      method: "DELETE"
    });
  },
  captureDouyin(token: string, shareText: string, remark: string, tags: string[]) {
    return request<Note>("/captures/douyin", token, {
      method: "POST",
      body: JSON.stringify({ share_text: shareText, remark, tags })
    });
  },
  captureScreenshot(token: string, file: File, remark: string, tags: string[]) {
    const form = new FormData();
    form.append("file", file);
    form.append("remark", remark);
    form.append("tags", tags.join(","));
    return request<Note>("/captures/screenshot", token, {
      method: "POST",
      body: form
    });
  }
};
