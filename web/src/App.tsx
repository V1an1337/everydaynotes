import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock,
  ImageUp,
  Link,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Tag,
  Trash2,
  Upload,
  Video,
  X,
  ZoomIn,
  ZoomOut
} from "lucide-react";
import { FormEvent, WheelEvent, useEffect, useMemo, useState } from "react";
import { api, assetUrl } from "./api";
import type { Asset, Note } from "./types";

const TOKEN_KEY = "everydaynotes.token";

type DateGrain = "day" | "week" | "month" | "year";
type ImageViewer = { src: string; alt: string } | null;
type TimelineItem = { day: string; label: string; shortLabel: string; count: number };

const dateGrains: Array<{ value: DateGrain; label: string; title: string }> = [
  { value: "day", label: "日", title: "按日查看" },
  { value: "week", label: "周", title: "按周查看" },
  { value: "month", label: "月", title: "按月查看" },
  { value: "year", label: "年", title: "按年查看" }
];

const noteTypes = [
  { value: "", label: "All" },
  { value: "screenshot", label: "Screenshot" },
  { value: "douyin", label: "Douyin" }
];

function dateGrainLabel(grain: DateGrain): string {
  return dateGrains.find((item) => item.value === grain)?.label ?? "";
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatGroupDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric"
  }).format(new Date(value));
}

function formatTimelineDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric"
  }).format(new Date(value));
}

function formatInputDate(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function parseInputDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, (month || 1) - 1, day || 1);
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function startOfWeek(date: Date): Date {
  const start = startOfDay(date);
  const day = start.getDay();
  start.setDate(start.getDate() - ((day + 6) % 7));
  return start;
}

function addDateRange(anchor: Date, grain: DateGrain, amount: number): Date {
  const next = new Date(anchor);
  if (grain === "day") next.setDate(next.getDate() + amount);
  if (grain === "week") next.setDate(next.getDate() + amount * 7);
  if (grain === "month") next.setMonth(next.getMonth() + amount);
  if (grain === "year") next.setFullYear(next.getFullYear() + amount);
  return next;
}

function dateRange(anchorInput: string, grain: DateGrain) {
  const anchor = parseInputDate(anchorInput);
  let from: Date;
  let endExclusive: Date;

  if (grain === "day") {
    from = startOfDay(anchor);
    endExclusive = new Date(from);
    endExclusive.setDate(endExclusive.getDate() + 1);
  } else if (grain === "week") {
    from = startOfWeek(anchor);
    endExclusive = new Date(from);
    endExclusive.setDate(endExclusive.getDate() + 7);
  } else if (grain === "month") {
    from = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
    endExclusive = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 1);
  } else {
    from = new Date(anchor.getFullYear(), 0, 1);
    endExclusive = new Date(anchor.getFullYear() + 1, 0, 1);
  }

  const to = new Date(endExclusive.getTime() - 1);
  const label = formatRangeLabel(from, to, grain);
  return { from, to, label };
}

function formatRangeLabel(from: Date, to: Date, grain: DateGrain): string {
  if (grain === "day") {
    return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long", day: "numeric" }).format(from);
  }
  if (grain === "week") {
    const formatter = new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric" });
    return `${formatter.format(from)} - ${formatter.format(to)}`;
  }
  if (grain === "month") {
    return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long" }).format(from);
  }
  return `${from.getFullYear()} 年`;
}

function splitTags(value: string): string[] {
  return value
    .split(/[,，\s]+/)
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

function isImageAsset(asset: Asset): boolean {
  return asset.mime_type?.startsWith("image") ?? /\.(png|jpe?g|webp|gif)$/i.test(asset.file_name);
}

function groupNotesByDay(notes: Note[]) {
  const groups = new Map<string, Note[]>();
  for (const note of notes) {
    const key = formatInputDate(new Date(note.created_at));
    groups.set(key, [...(groups.get(key) ?? []), note]);
  }
  return Array.from(groups.entries()).map(([day, items]) => ({
    day,
    label: formatGroupDate(items[0].created_at),
    items
  }));
}

function timelineItems(notes: Note[], anchorDate: string, currentRangeLabel: string): TimelineItem[] {
  const groups = groupNotesByDay(notes);
  if (!groups.length) {
    return [
      {
        day: anchorDate,
        label: currentRangeLabel,
        shortLabel: formatTimelineDate(anchorDate),
        count: 0
      }
    ];
  }
  return groups.slice(0, 12).map((group) => ({
    day: group.day,
    label: group.label,
    shortLabel: formatTimelineDate(group.day),
    count: group.items.length
  }));
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
          <p className="brand-mark">EverydayNotes</p>
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

function MediaPreview({
  note,
  token,
  large = false,
  onImageOpen
}: {
  note: Note;
  token: string;
  large?: boolean;
  onImageOpen?: (image: ImageViewer) => void;
}) {
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

  const src = assetUrl(asset, token);
  const image = <img className={`media ${large ? "large" : ""}`} src={src} alt={note.title ?? "note"} />;
  if (large && isImageAsset(asset) && onImageOpen) {
    return (
      <button className="media-open" onClick={() => onImageOpen({ src, alt: note.title ?? asset.file_name })}>
        {image}
        <span>点击查看大图</span>
      </button>
    );
  }
  return image;
}

function NoteCard({ note, token, onOpen }: { note: Note; token: string; onOpen: () => void }) {
  return (
    <button className="note-card" onClick={onOpen}>
      <MediaPreview note={note} token={token} />
      <div className="note-card-copy">
        <p className="note-title">{note.title || (note.type === "douyin" ? "Douyin Capture" : "Screenshot")}</p>
        <div className="note-meta">
          <span>{note.author || (note.type === "douyin" ? "Douyin" : "Screenshot")}</span>
          <span>{formatDate(note.created_at)}</span>
        </div>
        {note.tags.length ? (
          <div className="tag-row">
            {note.tags.slice(0, 3).map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        ) : null}
      </div>
    </button>
  );
}

function TimelineRail({
  items,
  activeDay,
  onSelectDay,
  onToday
}: {
  items: TimelineItem[];
  activeDay: string;
  onSelectDay: (day: string) => void;
  onToday: () => void;
}) {
  return (
    <aside className="timeline-rail" aria-label="日期时间轴">
      <div className="timeline-track">
        <div className="timeline-line" />
        {items.map((item) => {
          const active = item.day === activeDay;
          return (
            <button key={item.day} className={`timeline-dot-row ${active ? "active" : ""}`} onClick={() => onSelectDay(item.day)}>
              <span className="timeline-dot" />
              <span>{item.shortLabel}</span>
              {item.count ? <em>{item.count}</em> : null}
            </button>
          );
        })}
      </div>
      <button className="today-button" onClick={onToday}>
        Today
      </button>
    </aside>
  );
}

function CapturePanel({ token, onSaved, onClose }: { token: string; onSaved: (note: Note) => void; onClose: () => void }) {
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
        onClose();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="capture-panel">
      <div className="panel-title-row">
        <div>
          <p className="section-label">Import</p>
          <h2>手动保存</h2>
        </div>
        <button className="icon-button" onClick={onClose} aria-label="关闭导入面板">
          <X size={18} />
        </button>
      </div>
      <div className="segmented" aria-label="capture type">
        <button className={mode === "douyin" ? "active" : ""} onClick={() => setMode("douyin")} type="button">
          <Link size={15} />
          Douyin
        </button>
        <button className={mode === "screenshot" ? "active" : ""} onClick={() => setMode("screenshot")} type="button">
          <ImageUp size={15} />
          Screenshot
        </button>
      </div>
      <form className="capture-form" onSubmit={submit}>
        {mode === "douyin" ? (
          <textarea value={shareText} onChange={(event) => setShareText(event.target.value)} placeholder="粘贴抖音分享文本" />
        ) : (
          <label className="file-input">
            <Upload size={18} />
            <span>{file ? file.name : "选择截图"}</span>
            <input type="file" accept="image/*" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </label>
        )}
        <input value={remark} onChange={(event) => setRemark(event.target.value)} placeholder="备注" />
        <input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="标签，用空格或逗号分隔" />
        {error ? <p className="error">{error}</p> : null}
        <button className="primary icon-text" disabled={saving || (mode === "douyin" ? !shareText : !file)}>
          <Upload size={18} />
          {saving ? "保存中" : "保存"}
        </button>
      </form>
    </section>
  );
}

function Detail({
  note,
  token,
  onClose,
  onUpdated,
  onDeleted,
  onImageOpen
}: {
  note: Note;
  token: string;
  onClose: () => void;
  onUpdated: (note: Note) => void;
  onDeleted: (id: string) => void;
  onImageOpen: (image: ImageViewer) => void;
}) {
  const [remark, setRemark] = useState(note.remark ?? "");
  const [tags, setTags] = useState(note.tags.join(" "));
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setRemark(note.remark ?? "");
    setTags(note.tags.join(" "));
    setConfirmDelete(false);
    setError("");
  }, [note.id, note.remark, note.tags]);

  async function save() {
    setSaving(true);
    setError("");
    try {
      onUpdated(await api.updateNote(token, note.id, { remark, tags: splitTags(tags) }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    setDeleting(true);
    setError("");
    try {
      await api.deleteNote(token, note.id);
      onDeleted(note.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <aside className="detail-panel">
      <div className="detail-header">
        <div>
          <p className="section-label">{formatGroupDate(note.created_at)}</p>
          <h2>{note.title || "Untitled"}</h2>
        </div>
        <button className="icon-button" onClick={onClose} aria-label="close">
          <X size={20} />
        </button>
      </div>
      <MediaPreview note={note} token={token} large onImageOpen={onImageOpen} />
      <div className="detail-grid">
        {note.author ? (
          <p>
            <strong>作者</strong>
            <span>{note.author}</span>
          </p>
        ) : null}
        {note.source_url ? (
          <p>
            <strong>来源</strong>
            <a href={note.source_url} target="_blank" rel="noreferrer">
              打开
            </a>
          </p>
        ) : null}
        <p>
          <strong>状态</strong>
          <span>{note.status}</span>
        </p>
      </div>
      <label>
        <span>备注</span>
        <textarea value={remark} onChange={(event) => setRemark(event.target.value)} />
      </label>
      <label>
        <span>标签</span>
        <input value={tags} onChange={(event) => setTags(event.target.value)} />
      </label>
      {error ? <p className="error">{error}</p> : null}
      <div className="detail-actions">
        <button className="primary" onClick={save} disabled={saving}>
          {saving ? "保存中" : "保存修改"}
        </button>
        <button className="danger-button icon-text" onClick={() => setConfirmDelete(true)}>
          <Trash2 size={18} />
          删除记录
        </button>
      </div>
      {note.ocr_text ? (
        <section className="text-block">
          <p className="section-label">OCR</p>
          <p>{note.ocr_text}</p>
        </section>
      ) : null}
      {note.source_text ? (
        <section className="text-block">
          <p className="section-label">Share</p>
          <p>{note.source_text}</p>
        </section>
      ) : null}
      {confirmDelete ? (
        <div className="confirm-popover" role="dialog" aria-modal="true" aria-label="confirm delete">
          <div>
            <p className="confirm-title">确认删除这条记录？</p>
            <p className="confirm-copy">这会删除记录和服务器上的关联媒体文件，操作不可撤销。</p>
          </div>
          <div className="confirm-actions">
            <button className="subtle-button" onClick={() => setConfirmDelete(false)} disabled={deleting}>
              取消
            </button>
            <button className="danger-button" onClick={remove} disabled={deleting}>
              {deleting ? "删除中" : "确认删除"}
            </button>
          </div>
        </div>
      ) : null}
    </aside>
  );
}

function ImageLightbox({ image, onClose }: { image: ImageViewer; onClose: () => void }) {
  const [zoom, setZoom] = useState(1);
  if (!image) return null;

  function updateZoom(next: number) {
    setZoom(Math.min(4, Math.max(0.5, next)));
  }

  function handleWheel(event: WheelEvent<HTMLDivElement>) {
    event.preventDefault();
    updateZoom(zoom + (event.deltaY > 0 ? -0.15 : 0.15));
  }

  return (
    <div className="lightbox" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="lightbox-toolbar" onClick={(event) => event.stopPropagation()}>
        <button className="icon-button" onClick={() => updateZoom(zoom - 0.25)} aria-label="zoom out">
          <ZoomOut size={19} />
        </button>
        <span>{Math.round(zoom * 100)}%</span>
        <button className="icon-button" onClick={() => updateZoom(zoom + 0.25)} aria-label="zoom in">
          <ZoomIn size={19} />
        </button>
        <button className="icon-button" onClick={() => setZoom(1)} aria-label="reset zoom">
          <RotateCcw size={18} />
        </button>
        <button className="icon-button" onClick={onClose} aria-label="close image preview">
          <X size={20} />
        </button>
      </div>
      <div className="lightbox-stage" onWheel={handleWheel} onClick={(event) => event.stopPropagation()}>
        <img src={image.src} alt={image.alt} style={{ transform: `scale(${zoom})` }} />
      </div>
    </div>
  );
}

export function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [notes, setNotes] = useState<Note[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [tag, setTag] = useState("");
  const [dateGrain, setDateGrain] = useState<DateGrain>("month");
  const [anchorDate, setAnchorDate] = useState(formatInputDate(new Date()));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [captureOpen, setCaptureOpen] = useState(false);
  const [imageViewer, setImageViewer] = useState<ImageViewer>(null);

  const selected = useMemo(() => notes.find((note) => note.id === selectedId) ?? null, [notes, selectedId]);
  const currentRange = useMemo(() => dateRange(anchorDate, dateGrain), [anchorDate, dateGrain]);
  const groupedNotes = useMemo(() => groupNotesByDay(notes), [notes]);
  const railItems = useMemo(() => timelineItems(notes, anchorDate, currentRange.label), [notes, anchorDate, currentRange.label]);
  const activeRailDay = useMemo(
    () => (railItems.some((item) => item.day === anchorDate) ? anchorDate : (railItems[0]?.day ?? anchorDate)),
    [anchorDate, railItems]
  );

  async function refresh() {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const result = await api.listNotes(token, {
        query,
        type,
        tag,
        from: currentRange.from.toISOString(),
        to: currentRange.to.toISOString()
      });
      setNotes(result.items);
      setSelectedId((current) => (current && result.items.some((note) => note.id === current) ? current : null));
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function openRandom() {
    setError("");
    try {
      const note = await api.random(token ?? "");
      setNotes((current) => [note, ...current.filter((item) => item.id !== note.id)]);
      setSelectedId(note.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "没有可随机查看的记录");
    }
  }

  useEffect(() => {
    refresh();
  }, [token, dateGrain, anchorDate, type]);

  if (!token) {
    return <Login onLogin={setToken} />;
  }

  return (
    <div className="web-shell">
      <TimelineRail
        items={railItems}
        activeDay={activeRailDay}
        onSelectDay={setAnchorDate}
        onToday={() => setAnchorDate(formatInputDate(new Date()))}
      />

      <div className="content-shell">
        <header className="app-header">
          <div className="brand-line">
            <h1>EverydayNotes</h1>
            <nav aria-label="主导航">
              <button className="active" type="button">
                今日以前
              </button>
              <button type="button" onClick={openRandom}>
                随机看看
              </button>
            </nav>
          </div>
          <button
            className="logout-button"
            onClick={() => {
              localStorage.removeItem(TOKEN_KEY);
              setToken(null);
            }}
          >
            LogOut
          </button>
        </header>

        <section className="filter-strip">
          <div className="type-chips" aria-label="记录类型">
            {noteTypes.map((item) => (
              <button
                key={item.value}
                className={type === item.value ? "active" : ""}
                onClick={() => setType(item.value)}
                type="button"
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="filter-actions">
            <button className="icon-button ghost" onClick={() => setFiltersOpen((value) => !value)} aria-label="搜索与筛选">
              <Search size={18} />
            </button>
            <button className="icon-button ghost" onClick={() => setCaptureOpen((value) => !value)} aria-label="手动导入">
              <Upload size={18} />
            </button>
          </div>
        </section>

        {filtersOpen ? (
          <section className="drawer-band">
            <label className="search-box">
              <Search size={18} />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => event.key === "Enter" && refresh()}
                placeholder="搜索标题、备注、OCR、分享文本"
              />
            </label>
            <label className="tag-filter">
              <Tag size={17} />
              <input
                value={tag}
                onChange={(event) => setTag(event.target.value)}
                onKeyDown={(event) => event.key === "Enter" && refresh()}
                placeholder="标签"
              />
            </label>
            <div className="range-field" aria-label="当前搜索时间范围">
              <CalendarDays size={17} />
              <div>
                <span>{dateGrainLabel(dateGrain)}粒度搜索范围</span>
                <strong>{currentRange.label}</strong>
              </div>
              <input
                type="date"
                value={anchorDate}
                onChange={(event) => setAnchorDate(event.target.value)}
                aria-label="定位日期"
              />
            </div>
            <button className="subtle-button icon-text" onClick={refresh} disabled={loading}>
              <SlidersHorizontal size={17} />
              {loading ? "加载中" : "应用筛选"}
            </button>
          </section>
        ) : null}

        {captureOpen ? (
          <CapturePanel
            token={token}
            onClose={() => setCaptureOpen(false)}
            onSaved={(note) => {
              setNotes((current) => [note, ...current]);
              setSelectedId(note.id);
            }}
          />
        ) : null}

        <main className="gallery-canvas">
          <section className="range-title-row">
            <div className="range-controls">
              <button className="icon-button compact" onClick={() => setAnchorDate(formatInputDate(addDateRange(parseInputDate(anchorDate), dateGrain, -1)))} aria-label="上一个时间范围">
                <ChevronLeft size={18} />
              </button>
              <button className="icon-button compact" onClick={() => setAnchorDate(formatInputDate(addDateRange(parseInputDate(anchorDate), dateGrain, 1)))} aria-label="下一个时间范围">
                <ChevronRight size={18} />
              </button>
            </div>
            <h2>{currentRange.label}</h2>
            <div className="title-rule" />
          </section>

          {error ? <p className="error">{error}</p> : null}

          <section className="timeline">
            {groupedNotes.map((group) => (
              <section className="day-group" key={group.day}>
                <div className="day-heading">
                  <h3>{group.label}</h3>
                  <span>{group.items.length} 条</span>
                </div>
                <div className="day-grid">
                  {group.items.map((note) => (
                    <NoteCard key={note.id} note={note} token={token} onOpen={() => setSelectedId(note.id)} />
                  ))}
                </div>
              </section>
            ))}
            {!loading && notes.length === 0 ? <p className="empty">这个时间段还没有记录</p> : null}
            {loading ? <p className="empty">正在翻找你的旧日碎片...</p> : null}
          </section>
        </main>
      </div>

      <nav className="grain-floating" aria-label="日期粒度">
        {dateGrains.map((item) => (
          <button
            key={item.value}
            className={dateGrain === item.value ? "active" : ""}
            onClick={() => setDateGrain(item.value)}
            title={item.title}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </nav>

      {selected ? (
        <Detail
          note={selected}
          token={token}
          onClose={() => setSelectedId(null)}
          onUpdated={(note) => {
            setNotes((current) => current.map((item) => (item.id === note.id ? note : item)));
          }}
          onDeleted={(id) => {
            setNotes((current) => current.filter((item) => item.id !== id));
            setSelectedId(null);
          }}
          onImageOpen={setImageViewer}
        />
      ) : null}
      <ImageLightbox image={imageViewer} onClose={() => setImageViewer(null)} />
    </div>
  );
}
