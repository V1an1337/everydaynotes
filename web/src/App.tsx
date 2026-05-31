import {
  Clock,
  ImageUp,
  Link,
  LogOut,
  Search,
  Shuffle,
  Tag,
  Upload,
  Video,
  X
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, assetUrl } from "./api";
import type { Asset, Note } from "./types";

const TOKEN_KEY = "everydaynotes.token";

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function splitTags(value: string): string[] {
  return value
    .split(/[,，#\s]+/)
    .map((tag) => tag.trim().toLowerCase())
    .filter(Boolean);
}

function primaryAsset(note: Note): Asset | undefined {
  return (
    note.assets.find((asset) => asset.kind === "video") ??
    note.assets.find((asset) => asset.kind === "screenshot") ??
    note.assets.find((asset) => asset.kind === "cover") ??
    note.assets[0]
  );
}

function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const result = await api.login(password);
      localStorage.setItem(TOKEN_KEY, result.access_token);
      onLogin(result.access_token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <form className="login-panel" onSubmit={submit}>
        <div>
          <p className="eyebrow">EverydayNotes</p>
          <h1>私人日记库</h1>
        </div>
        <label>
          <span>密码</span>
          <input
            autoFocus
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="••••••••"
          />
        </label>
        {error ? <p className="error">{error}</p> : null}
        <button className="primary" disabled={loading || !password}>
          {loading ? "登录中" : "进入"}
        </button>
      </form>
    </main>
  );
}

function MediaPreview({ note, token, large = false }: { note: Note; token: string; large?: boolean }) {
  const asset = primaryAsset(note);
  const cover = note.assets.find((item) => item.kind === "cover");
  if (!asset) {
    return <div className={`media-placeholder ${large ? "large" : ""}`}>{note.type === "douyin" ? <Video /> : <Clock />}</div>;
  }
  if (asset.kind === "video" || asset.mime_type?.startsWith("video")) {
    return (
      <video
        className={`media ${large ? "large" : ""}`}
        controls={large}
        muted={!large}
        poster={cover ? assetUrl(cover, token) : undefined}
        src={assetUrl(asset, token)}
      />
    );
  }
  return <img className={`media ${large ? "large" : ""}`} src={assetUrl(asset, token)} alt={note.title ?? "note"} />;
}

function NoteCard({ note, token, onOpen }: { note: Note; token: string; onOpen: () => void }) {
  return (
    <button className="note-card" onClick={onOpen}>
      <MediaPreview note={note} token={token} />
      <span className={`status ${note.status}`}>{note.status}</span>
      <div className="note-card-copy">
        <p className="note-title">{note.title || (note.type === "douyin" ? "Douyin capture" : "Screenshot")}</p>
        <p className="note-meta">
          {note.author ? `${note.author} · ` : ""}
          {formatDate(note.created_at)}
        </p>
        {note.remark ? <p className="remark">{note.remark}</p> : null}
        {note.tags.length ? (
          <div className="tag-row">
            {note.tags.slice(0, 4).map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        ) : null}
      </div>
    </button>
  );
}

function CapturePanel({ token, onSaved }: { token: string; onSaved: (note: Note) => void }) {
  const [mode, setMode] = useState<"douyin" | "screenshot">("douyin");
  const [shareText, setShareText] = useState("");
  const [remark, setRemark] = useState("");
  const [tags, setTags] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const tagList = splitTags(tags);
      const note =
        mode === "douyin"
          ? await api.captureDouyin(token, shareText, remark, tagList)
          : file
            ? await api.captureScreenshot(token, file, remark, tagList)
            : null;
      if (note) {
        onSaved(note);
        setShareText("");
        setRemark("");
        setTags("");
        setFile(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="capture-panel">
      <div className="segmented" aria-label="capture type">
        <button className={mode === "douyin" ? "active" : ""} onClick={() => setMode("douyin")} type="button">
          <Link size={16} />
          Douyin
        </button>
        <button className={mode === "screenshot" ? "active" : ""} onClick={() => setMode("screenshot")} type="button">
          <ImageUp size={16} />
          Screenshot
        </button>
      </div>
      <form className="capture-form" onSubmit={submit}>
        {mode === "douyin" ? (
          <textarea value={shareText} onChange={(event) => setShareText(event.target.value)} placeholder="抖音分享文本" />
        ) : (
          <label className="file-input">
            <Upload size={18} />
            <span>{file ? file.name : "选择截图"}</span>
            <input type="file" accept="image/*" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </label>
        )}
        <input value={remark} onChange={(event) => setRemark(event.target.value)} placeholder="备注" />
        <input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="标签" />
        {error ? <p className="error">{error}</p> : null}
        <button className="primary icon-text" disabled={saving || (mode === "douyin" ? !shareText : !file)}>
          <Upload size={18} />
          {saving ? "保存中" : "保存"}
        </button>
      </form>
    </section>
  );
}

function Detail({ note, token, onClose, onUpdated }: { note: Note; token: string; onClose: () => void; onUpdated: (note: Note) => void }) {
  const [remark, setRemark] = useState(note.remark ?? "");
  const [tags, setTags] = useState(note.tags.join(" "));
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    try {
      onUpdated(await api.updateNote(token, note.id, { remark, tags: splitTags(tags) }));
    } finally {
      setSaving(false);
    }
  }

  return (
    <aside className="detail-panel">
      <div className="detail-header">
        <div>
          <p className="eyebrow">{formatDate(note.created_at)}</p>
          <h2>{note.title || "Untitled"}</h2>
        </div>
        <button className="icon-button" onClick={onClose} aria-label="close">
          <X size={20} />
        </button>
      </div>
      <MediaPreview note={note} token={token} large />
      <div className="detail-grid">
        {note.author ? <p><strong>作者</strong><span>{note.author}</span></p> : null}
        {note.source_url ? <p><strong>来源</strong><a href={note.source_url} target="_blank" rel="noreferrer">打开</a></p> : null}
        <p><strong>状态</strong><span>{note.status}</span></p>
      </div>
      <label>
        <span>备注</span>
        <textarea value={remark} onChange={(event) => setRemark(event.target.value)} />
      </label>
      <label>
        <span>标签</span>
        <input value={tags} onChange={(event) => setTags(event.target.value)} />
      </label>
      <button className="primary" onClick={save} disabled={saving}>
        {saving ? "保存中" : "保存修改"}
      </button>
      {note.ocr_text ? (
        <section className="text-block">
          <p className="eyebrow">OCR</p>
          <p>{note.ocr_text}</p>
        </section>
      ) : null}
      {note.source_text ? (
        <section className="text-block">
          <p className="eyebrow">Share</p>
          <p>{note.source_text}</p>
        </section>
      ) : null}
    </aside>
  );
}

export function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [notes, setNotes] = useState<Note[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [tag, setTag] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const selected = useMemo(() => notes.find((note) => note.id === selectedId) ?? null, [notes, selectedId]);

  async function refresh() {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const result = await api.listNotes(token, { query, type, tag });
      setNotes(result.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, [token]);

  if (!token) {
    return <Login onLogin={setToken} />;
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">EverydayNotes</p>
          <h1>今日以前</h1>
        </div>
        <div className="topbar-actions">
          <button
            className="icon-text"
            onClick={async () => {
              const note = await api.random(token);
              setNotes((current) => [note, ...current.filter((item) => item.id !== note.id)]);
              setSelectedId(note.id);
            }}
          >
            <Shuffle size={18} />
            盲盒
          </button>
          <button
            className="icon-button"
            onClick={() => {
              localStorage.removeItem(TOKEN_KEY);
              setToken(null);
            }}
            aria-label="logout"
          >
            <LogOut size={19} />
          </button>
        </div>
      </header>

      <section className="toolbar">
        <label className="search-box">
          <Search size={18} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => event.key === "Enter" && refresh()} placeholder="搜索" />
        </label>
        <select value={type} onChange={(event) => setType(event.target.value)} aria-label="type">
          <option value="">全部</option>
          <option value="screenshot">截图</option>
          <option value="douyin">抖音</option>
        </select>
        <label className="tag-filter">
          <Tag size={17} />
          <input value={tag} onChange={(event) => setTag(event.target.value)} placeholder="标签" />
        </label>
        <button onClick={refresh} disabled={loading}>
          {loading ? "加载中" : "刷新"}
        </button>
      </section>

      <CapturePanel
        token={token}
        onSaved={(note) => {
          setNotes((current) => [note, ...current]);
          setSelectedId(note.id);
        }}
      />

      {error ? <p className="error">{error}</p> : null}

      <section className="timeline">
        {notes.map((note) => (
          <NoteCard key={note.id} note={note} token={token} onOpen={() => setSelectedId(note.id)} />
        ))}
        {!loading && notes.length === 0 ? <p className="empty">还没有记录</p> : null}
      </section>

      {selected ? (
        <Detail
          note={selected}
          token={token}
          onClose={() => setSelectedId(null)}
          onUpdated={(note) => {
            setNotes((current) => current.map((item) => (item.id === note.id ? note : item)));
          }}
        />
      ) : null}
    </main>
  );
}

