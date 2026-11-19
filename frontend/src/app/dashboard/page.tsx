"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Archive,
  Award,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Database,
  GraduationCap,
  Lock,
  LogOut,
  MessageSquare,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Send,
  ShieldAlert,
  ThumbsUp,
  Trash2,
  Trophy,
  UserCheck,
} from "lucide-react";

const TOKEN_KEY = "doubtPortalAuthToken";

interface AuthUser {
  userId: number;
  name: string;
  email: string;
  role: "STUDENT" | "TEACHER" | "ADMIN";
  detail?: string;
  token?: string;
}

interface SubjectItem {
  subjectId: number;
  subjectName: string;
  subjectCode: string;
  teacherName?: string;
}

interface StudentAnswer {
  answerId: number;
  doubtId: number;
  studentId: number;
  studentName?: string;
  answerText: string;
  upvotes: number;
  isHelpful: boolean;
}

interface TeacherAnswer {
  teacherAnswerId: number;
  doubtId: number;
  teacherId: number;
  teacherName?: string;
  answerText: string;
  upvotes: number;
}

interface DoubtItem {
  doubtId: number;
  studentId?: number | null;
  subjectId: number;
  subjectName?: string;
  subjectCode?: string;
  teacherName?: string;
  title: string;
  description?: string;
  isAnonymous: boolean;
  authorDisplay: string;
  priority: "Low" | "Medium" | "High";
  status: string;
  relevanceScore?: number;
  tags: string[];
  answers: StudentAnswer[];
  teacherAnswers: TeacherAnswer[];
}

interface VectorDoc {
  id: string;
  doubtId: number;
  title: string;
  tags: string[];
  vectorEmbedding: number[];
  similarityScore?: number | null;
}

interface LeaderboardRow {
  studentId: number;
  name: string;
  branch: string;
  totalAnswers: number;
  totalUpvotes: number;
  helpfulCount: number;
  score: number;
}

interface AuditData {
  deletedDoubts: Array<{
    doubtId: number;
    title: string;
    status: string;
    deletedByAdminId: number;
    adminName?: string;
    subjectCode?: string;
  }>;
  deletedAnswers: Array<{
    answerId: number;
    doubtId: number;
    answerText: string;
    deletedByAdminId: number;
    adminName?: string;
  }>;
}

export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [subjects, setSubjects] = useState<SubjectItem[]>([]);
  const [doubts, setDoubts] = useState<DoubtItem[]>([]);
  const [vectors, setVectors] = useState<VectorDoc[]>([]);
  const [leaderboard, setLeaderboard] = useState<LeaderboardRow[]>([]);
  const [audit, setAudit] = useState<AuditData>({ deletedDoubts: [], deletedAnswers: [] });

  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [answerDrafts, setAnswerDrafts] = useState<Record<number, string>>({});

  // Doubt filters & creation form
  const [searchQuery, setSearchQuery] = useState("");
  const [filterSubject, setFilterSubject] = useState("");
  const [filterStatus, setFilterStatus] = useState("");
  const [title, setTitle] = useState("");
  const [subjectId, setSubjectId] = useState<number>(1);
  const [priority, setPriority] = useState<"Low" | "Medium" | "High">("Medium");
  const [tagsInput, setTagsInput] = useState("");
  const [description, setDescription] = useState("");
  const [isAnonymous, setIsAnonymous] = useState(false);

  // Vector search & RUD controls
  const [vectorQuery, setVectorQuery] = useState("");
  const [vectorMode, setVectorMode] = useState<"semantic" | "tag">("semantic");
  const [editingVecId, setEditingVecId] = useState<string | null>(null);
  const [editingVecTags, setEditingVecTags] = useState("");

  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const initializedRef = useRef(false);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const apiFetch = useCallback(
    async (url: string, options: RequestInit = {}) => {
      const token = sessionStorage.getItem(TOKEN_KEY);
      const headers = new Headers(options.headers || {});
      if (token) {
        headers.set("X-Auth-Token", token);
      }
      const res = await fetch(url, {
        ...options,
        credentials: "include",
        headers,
      });
      if (res.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
        router.replace("/");
        throw new Error("Session expired.");
      }
      return res;
    },
    [router]
  );

  const notifySuccess = (msg: string) => {
    setErrorMsg("");
    setSuccessMsg(msg);
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    toastTimerRef.current = setTimeout(() => setSuccessMsg(""), 4500);
  };

  const notifyError = (msg: string) => {
    setSuccessMsg("");
    setErrorMsg(msg);
  };

  const loadSubjects = useCallback(async () => {
    const res = await apiFetch("/api/subjects");
    if (res.ok) {
      const data: SubjectItem[] = await res.json();
      setSubjects(data);
      if (data.length > 0) {
        setSubjectId((prev) => prev || data[0].subjectId);
      }
    }
  }, [apiFetch]);

  const loadDoubts = useCallback(
    async (subjectOverride?: string, statusOverride?: string, queryOverride?: string) => {
      const params = new URLSearchParams();
      const subVal = subjectOverride !== undefined ? subjectOverride : filterSubject;
      const stVal = statusOverride !== undefined ? statusOverride : filterStatus;
      const qVal = (queryOverride !== undefined ? queryOverride : searchQuery).trim();
      if (subVal) params.set("subjectId", subVal);
      if (stVal) params.set("status", stVal);
      if (qVal) params.set("q", qVal);
      const qs = params.toString();
      const res = await apiFetch(`/api/doubts${qs ? `?${qs}` : ""}`);
      if (res.ok) {
        const data: DoubtItem[] = await res.json();
        setDoubts(data);
        setExpandedId((prev) => (prev !== null ? prev : data[0]?.doubtId ?? null));
      }
    },
    [apiFetch, filterSubject, filterStatus, searchQuery]
  );

  const loadVectors = useCallback(
    async (queryOverride?: string, modeOverride?: "semantic" | "tag") => {
      const q = (queryOverride ?? vectorQuery).trim();
      const mode = modeOverride ?? vectorMode;
      const url = !q
        ? "/api/nosql/vectors"
        : mode === "semantic"
          ? `/api/nosql/vectors/semantic?q=${encodeURIComponent(q)}`
          : `/api/nosql/vectors/search?tag=${encodeURIComponent(q)}`;
      const res = await apiFetch(url);
      if (res.ok) {
        const data: VectorDoc[] = await res.json();
        setVectors(data);
      }
    },
    [apiFetch, vectorQuery, vectorMode]
  );

  const loadLeaderboard = useCallback(async () => {
    const res = await apiFetch("/api/doubts/leaderboard");
    if (res.ok) {
      setLeaderboard(await res.json());
    }
  }, [apiFetch]);

  const loadAuditLog = useCallback(async () => {
    const res = await apiFetch("/api/doubts/audit");
    if (res.ok) {
      setAudit(await res.json());
    }
  }, [apiFetch]);

  useEffect(() => {
    if (initializedRef.current) return;
    initializedRef.current = true;
    const init = async () => {
      try {
        const meRes = await apiFetch("/api/auth/me");
        if (!meRes.ok) {
          router.replace("/");
          return;
        }
        const me: AuthUser = await meRes.json();
        setUser(me);
        await Promise.all([
          loadSubjects(),
          loadDoubts("", "", ""),
          loadVectors("", "semantic"),
          loadLeaderboard(),
        ]);
        if (me.role === "ADMIN") {
          await loadAuditLog();
        }
      } catch {
        // Redirect already triggered on 401
      }
    };
    init();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleLogout() {
    await fetch("/api/auth/logout", {
      method: "POST",
      credentials: "include",
      headers: sessionStorage.getItem(TOKEN_KEY)
        ? { "X-Auth-Token": sessionStorage.getItem(TOKEN_KEY) as string }
        : {},
    }).catch(() => {});
    sessionStorage.removeItem(TOKEN_KEY);
    router.push("/");
  }

  async function handleQuickSwitch(val: string) {
    if (!val) return;
    const [r, em, pwd] = val.split("|");
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: sessionStorage.getItem(TOKEN_KEY)
          ? { "X-Auth-Token": sessionStorage.getItem(TOKEN_KEY) as string }
          : {},
      });
      const res = await fetch("/api/auth/login", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ role: r, email: em, password: pwd }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to switch account.");
      if (data.token) sessionStorage.setItem(TOKEN_KEY, data.token);
      setUser(data);
      setErrorMsg("");
      setAnswerDrafts({});
      setEditingVecId(null);
      await loadDoubts();
      await loadVectors();
      await loadLeaderboard();
      if (data.role === "ADMIN") {
        await loadAuditLog();
      }
      notifySuccess(`Switched active session to ${data.name} (${data.role}).`);
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Switch failed.");
    }
  }

  async function handleCreateDoubt(e: React.FormEvent) {
    e.preventDefault();
    const tags = tagsInput
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    try {
      const res = await apiFetch("/api/doubts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          subjectId,
          title: title.trim(),
          description: description.trim(),
          isAnonymous,
          priority,
          tags,
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to submit question.");
      setTitle("");
      setDescription("");
      setTagsInput("");
      setIsAnonymous(false);
      notifySuccess("Doubt submitted successfully!");
      await loadDoubts();
      await loadVectors();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Failed to submit doubt.");
    }
  }

  async function handlePostAnswer(doubtId: number, isTeacher: boolean) {
    const answerText = (answerDrafts[doubtId] || "").trim();
    if (!answerText) return notifyError("Answer text cannot be empty.");
    const endpoint = isTeacher
      ? `/api/doubts/${doubtId}/teacher-answers`
      : `/api/doubts/${doubtId}/answers`;
    try {
      const res = await apiFetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answerText }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to post answer.");
      setAnswerDrafts((prev) => ({ ...prev, [doubtId]: "" }));
      notifySuccess(data.message || "Answer posted!");
      await loadDoubts();
      await loadLeaderboard();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Unable to post answer.");
    }
  }

  async function handleUpvotePeer(answerId: number) {
    try {
      const res = await apiFetch(`/api/doubts/answers/${answerId}/upvote`, { method: "POST" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Already upvoted.");
      notifySuccess(data.message);
      await loadDoubts();
      await loadLeaderboard();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Upvote failed.");
    }
  }

  async function handleUpvoteTeacher(teacherAnswerId: number) {
    try {
      const res = await apiFetch(`/api/doubts/teacher-answers/${teacherAnswerId}/upvote`, {
        method: "POST",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Already upvoted.");
      notifySuccess(data.message);
      await loadDoubts();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Upvote failed.");
    }
  }

  async function handleToggleHelpful(answerId: number, helpful: boolean) {
    try {
      const res = await apiFetch(`/api/doubts/answers/${answerId}/helpful?helpful=${helpful}`, {
        method: "PUT",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to mark helpful.");
      notifySuccess(data.message);
      await loadDoubts();
      await loadLeaderboard();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Update failed.");
    }
  }

  async function handleCloseDoubt(doubtId: number) {
    try {
      const res = await apiFetch(`/api/doubts/${doubtId}/close`, { method: "PUT" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to close doubt.");
      notifySuccess(data.message);
      await loadDoubts();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Close failed.");
    }
  }

  async function handleDeleteDoubt(doubtId: number, hard: boolean) {
    if (
      hard &&
      !window.confirm(
        `Permanently delete Doubt #${doubtId}? This will also remove all its answers, upvotes, and tags.`
      )
    ) {
      return;
    }
    try {
      const res = await apiFetch(`/api/doubts/${doubtId}?hard=${hard}`, { method: "DELETE" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to delete doubt.");
      notifySuccess(data.message);
      await loadDoubts();
      await loadVectors();
      await loadAuditLog();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Delete failed.");
    }
  }

  async function handleSoftDeleteAnswer(answerId: number) {
    try {
      const res = await apiFetch(`/api/doubts/answers/${answerId}`, { method: "DELETE" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to moderate answer.");
      notifySuccess(data.message);
      await loadDoubts();
      await loadLeaderboard();
      await loadAuditLog();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Moderation failed.");
    }
  }

  async function handleRestoreDoubt(doubtId: number) {
    try {
      const res = await apiFetch(`/api/doubts/${doubtId}/restore`, { method: "PUT" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to restore doubt.");
      notifySuccess(data.message);
      await loadDoubts();
      await loadVectors();
      await loadAuditLog();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Restore failed.");
    }
  }

  async function handleSaveVectorTags(id: string) {
    const tags = editingVecTags
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    try {
      const res = await apiFetch(`/api/nosql/vectors/${encodeURIComponent(id)}/tags`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(tags),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || "Unable to update topic tags.");
      setEditingVecId(null);
      notifySuccess("Topic tags updated successfully!");
      await loadVectors();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Update failed.");
    }
  }

  async function handleDeleteVector(id: string) {
    try {
      const res = await apiFetch(`/api/nosql/vectors/${encodeURIComponent(id)}`, {
        method: "DELETE",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Unable to remove topic entry.");
      notifySuccess(data.message);
      await loadVectors();
    } catch (err) {
      notifyError(err instanceof Error ? err.message : "Delete failed.");
    }
  }

  if (!user) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 text-slate-500 text-sm">
        Verifying session...
      </div>
    );
  }

  const canManageVectors = user.role === "TEACHER" || user.role === "ADMIN";

  return (
    <div className="min-h-screen bg-slate-50">
      <nav className="bg-slate-900 text-white border-b border-slate-800 sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 font-bold text-base sm:text-lg">
            <GraduationCap className="w-6 h-6 text-blue-400" />
            <span>Classroom Doubt Portal</span>
          </div>

          <div className="flex items-center gap-2.5 flex-wrap">
            <span
              className={`text-xs font-semibold px-3 py-1.5 rounded-full ${
                user.role === "STUDENT"
                  ? "bg-blue-600/20 text-blue-200 border border-blue-500/40"
                  : user.role === "TEACHER"
                    ? "bg-emerald-600/20 text-emerald-200 border border-emerald-500/40"
                    : "bg-rose-600/20 text-rose-200 border border-rose-500/40"
              }`}
            >
              {user.role}: {user.name} {user.detail ? `• ${user.detail}` : ""}
            </span>

            <select
              aria-label="Quick Switch Account"
              defaultValue=""
              onChange={(e) => {
                handleQuickSwitch(e.target.value);
                e.target.value = "";
              }}
              className="rounded-md bg-slate-800 border border-slate-700 text-xs text-slate-200 px-2.5 py-1.5"
            >
              <option value="">Quick Switch...</option>
              <option value="STUDENT|rahul@pes.edu|hashed_pwd_1">Student: Rahul Sharma</option>
              <option value="STUDENT|ananya@pes.edu|hashed_pwd_2">Student: Ananya Rao</option>
              <option value="STUDENT|vikram@pes.edu|hashed_pwd_3">Student: Vikram Verma</option>
              <option value="TEACHER|sudhir@pes.edu|prof_pwd_1">Teacher: Dr. Sudhir Kumar</option>
              <option value="ADMIN|admin@pes.edu|admin_root_pwd">Admin: System Admin</option>
            </select>

            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 rounded-md border border-slate-700 hover:bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-200"
            >
              <LogOut className="w-3.5 h-3.5" /> Logout
            </button>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto px-4 py-6 space-y-4">
        {/* Status Banners */}
        <div className="rounded-lg bg-white border border-slate-200 px-4 py-3 text-sm text-slate-700 flex items-center gap-2 shadow-sm">
          <UserCheck className="w-4 h-4 text-blue-600 shrink-0" />
          <span>
            Active Role: <strong className="text-slate-900">{user.role}</strong> —{" "}
            {user.role === "STUDENT"
              ? "Submit doubts (anonymously if preferred), write peer explanations, and upvote helpful answers."
              : user.role === "TEACHER"
                ? "Post verified faculty explanations, mark helpful peer answers, close resolved doubts, and curate topic tags."
                : "Moderate archived threads, restore or permanently delete posts, and manage topic indexes."}
          </span>
        </div>

        {successMsg && (
          <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-2.5 text-sm text-emerald-800 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}
        {errorMsg && (
          <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-2.5 text-sm text-red-700 flex items-center gap-2">
            <ShieldAlert className="w-4 h-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Left Column */}
          <div className="lg:col-span-5 space-y-5">
            {user.role === "STUDENT" && (
              <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
                <div className="bg-blue-600 text-white px-5 py-3.5 font-semibold text-sm flex items-center gap-2">
                  <Plus className="w-4 h-4" /> Ask a Question
                </div>
                <form onSubmit={handleCreateDoubt} className="p-5 space-y-3.5">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">Title</label>
                    <input
                      type="text"
                      required
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      placeholder="e.g., How does MVCC avoid dirty reads?"
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">Subject</label>
                      <select
                        value={subjectId}
                        onChange={(e) => setSubjectId(Number(e.target.value))}
                        className="w-full rounded-lg border border-slate-300 px-2.5 py-2 text-sm bg-white"
                      >
                        {subjects.map((s) => (
                          <option key={s.subjectId} value={s.subjectId}>
                            {s.subjectCode} — {s.subjectName}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">Priority</label>
                      <select
                        value={priority}
                        onChange={(e) => setPriority(e.target.value as "Low" | "Medium" | "High")}
                        className="w-full rounded-lg border border-slate-300 px-2.5 py-2 text-sm bg-white"
                      >
                        <option value="High">High</option>
                        <option value="Medium">Medium</option>
                        <option value="Low">Low</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Topic Tags (comma-separated)
                    </label>
                    <input
                      type="text"
                      value={tagsInput}
                      onChange={(e) => setTagsInput(e.target.value)}
                      placeholder="SQL, Indexing, ExamPrep"
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Detailed Description
                    </label>
                    <textarea
                      required
                      rows={3}
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="Explain what concept or error you are stuck on..."
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    />
                  </div>
                  <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={isAnonymous}
                      onChange={(e) => setIsAnonymous(e.target.checked)}
                      className="rounded border-slate-300"
                    />
                    Post Anonymously (Hide my identity)
                  </label>
                  <button
                    type="submit"
                    className="w-full flex items-center justify-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 text-sm"
                  >
                    <Send className="w-4 h-4" /> Submit Doubt
                  </button>
                </form>
              </section>
            )}

            {/* MongoDB Vector Search */}
            <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
                <div className="flex items-center gap-2 font-semibold text-sm text-slate-900">
                  <Database className="w-4 h-4 text-emerald-600" />
                  Vector Search
                </div>
                <span className="text-[11px] font-medium px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                  MongoDB NoSQL
                </span>
              </div>

              <div className="p-4 space-y-3">
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    setVectorMode("semantic");
                    loadVectors(vectorQuery, "semantic");
                  }}
                  className="flex gap-1.5"
                >
                  <input
                    type="text"
                    value={vectorQuery}
                    onChange={(e) => setVectorQuery(e.target.value)}
                    placeholder="e.g., B+ tree disk storage..."
                    className="flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
                  />
                  <button
                    type="submit"
                    className="rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white px-3 py-1.5 text-xs font-semibold"
                  >
                    Similar
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setVectorMode("tag");
                      loadVectors(vectorQuery, "tag");
                    }}
                    className="rounded-lg border border-slate-300 hover:bg-slate-50 px-2.5 py-1.5 text-xs font-medium text-slate-700"
                  >
                    Tag
                  </button>
                </form>

                <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
                  {vectors.length === 0 ? (
                    <div className="text-xs text-slate-500 italic py-2">
                      No matching topics found.
                    </div>
                  ) : (
                    vectors.map((vec) => (
                      <div
                        key={vec.id}
                        className="rounded-lg border border-slate-200 bg-slate-50/70 p-2.5 text-xs space-y-1.5"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div className="font-semibold text-slate-800">
                            {vec.doubtId ? `#${vec.doubtId}: ` : "Topic: "}
                            {vec.title}
                          </div>
                          <span className="shrink-0 rounded bg-emerald-100 text-emerald-800 px-1.5 py-0.5 font-medium">
                            {vec.similarityScore !== null && vec.similarityScore !== undefined
                              ? `${Math.round(vec.similarityScore * 100)}% match`
                              : "Indexed"}
                          </span>
                        </div>

                        <div className="flex flex-wrap gap-1">
                          {(vec.tags || []).map((t, idx) => (
                            <span
                              key={`${vec.id}-${idx}`}
                              className="px-1.5 py-0.5 rounded bg-slate-200/80 text-slate-700"
                            >
                              {t}
                            </span>
                          ))}
                        </div>

                        {canManageVectors && (
                          <div className="pt-1 flex items-center gap-2 flex-wrap">
                            {editingVecId === vec.id ? (
                              <div className="flex w-full gap-1.5">
                                <input
                                  type="text"
                                  value={editingVecTags}
                                  onChange={(e) => setEditingVecTags(e.target.value)}
                                  className="flex-1 rounded border border-slate-300 px-2 py-0.5 text-xs bg-white"
                                />
                                <button
                                  type="button"
                                  onClick={() => handleSaveVectorTags(vec.id)}
                                  className="rounded bg-blue-600 text-white px-2 py-0.5 font-medium"
                                >
                                  Save
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setEditingVecId(null)}
                                  className="rounded border border-slate-300 px-2 py-0.5"
                                >
                                  Cancel
                                </button>
                              </div>
                            ) : (
                              <>
                                <button
                                  type="button"
                                  onClick={() => {
                                    setEditingVecId(vec.id);
                                    setEditingVecTags((vec.tags || []).join(", "));
                                  }}
                                  className="inline-flex items-center gap-1 text-blue-700 hover:underline font-medium"
                                >
                                  <Pencil className="w-3 h-3" /> Edit Tags
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleDeleteVector(vec.id)}
                                  className="inline-flex items-center gap-1 text-rose-700 hover:underline font-medium"
                                >
                                  <Trash2 className="w-3 h-3" /> Delete
                                </button>
                              </>
                            )}
                          </div>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </section>

            {/* Contributor Leaderboard */}
            <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
                <div className="flex items-center gap-2 font-semibold text-sm text-slate-900">
                  <Trophy className="w-4 h-4 text-amber-500" />
                  Reputation Leaderboard
                </div>
                <button
                  type="button"
                  onClick={loadLeaderboard}
                  className="p-1 rounded hover:bg-slate-100 text-slate-500"
                  title="Refresh Leaderboard"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>
              <div className="divide-y divide-slate-100">
                {leaderboard.map((row, idx) => (
                  <div key={row.studentId} className="px-4 py-2.5 flex items-center justify-between text-xs">
                    <div>
                      <span className="font-bold text-slate-900">
                        #{idx + 1} {row.name}
                      </span>{" "}
                      <span className="text-slate-500">({row.branch})</span>
                      <div className="text-slate-500 mt-0.5">
                        {row.totalAnswers} {row.totalAnswers === 1 ? "answer" : "answers"} •{" "}
                        {row.totalUpvotes} {row.totalUpvotes === 1 ? "upvote" : "upvotes"} •{" "}
                        {row.helpfulCount} helpful
                      </div>
                    </div>
                    <span className="rounded-full bg-blue-50 text-blue-700 border border-blue-200 px-2.5 py-1 font-bold">
                      {row.score} pts
                    </span>
                  </div>
                ))}
              </div>
            </section>
          </div>

          {/* Right Column */}
          <div className="lg:col-span-7 space-y-5">
            <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="px-5 py-4 border-b border-slate-100 space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="font-semibold text-base text-slate-900 flex items-center gap-2">
                    <MessageSquare className="w-4 h-4 text-blue-600" />
                    Classroom Discussion Threads
                  </h2>
                  <button
                    type="button"
                    onClick={() => loadDoubts()}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <RefreshCw className="w-3.5 h-3.5" /> Refresh
                  </button>
                </div>

                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    loadDoubts();
                  }}
                  className="grid grid-cols-1 sm:grid-cols-12 gap-2"
                >
                  <div className="sm:col-span-5 relative">
                    <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Search doubts by keyword..."
                      className="w-full rounded-lg border border-slate-300 pl-8 pr-2.5 py-1.5 text-xs"
                    />
                  </div>
                  <select
                    value={filterSubject}
                    onChange={(e) => {
                      const next = e.target.value;
                      setFilterSubject(next);
                      loadDoubts(next, filterStatus, searchQuery);
                    }}
                    className="sm:col-span-3 rounded-lg border border-slate-300 px-2 py-1.5 text-xs bg-white"
                  >
                    <option value="">All Subjects</option>
                    {subjects.map((s) => (
                      <option key={s.subjectId} value={s.subjectId}>
                        {s.subjectCode}
                      </option>
                    ))}
                  </select>
                  <select
                    value={filterStatus}
                    onChange={(e) => {
                      const next = e.target.value;
                      setFilterStatus(next);
                      loadDoubts(filterSubject, next, searchQuery);
                    }}
                    className="sm:col-span-2 rounded-lg border border-slate-300 px-2 py-1.5 text-xs bg-white"
                  >
                    <option value="">All Status</option>
                    <option value="Pending">Pending</option>
                    <option value="Answered by Student">Peer Answered</option>
                    <option value="Answered by Teacher">Faculty Answered</option>
                    <option value="Closed">Closed</option>
                  </select>
                  <button
                    type="submit"
                    className="sm:col-span-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-white py-1.5 text-xs font-semibold"
                  >
                    Filter
                  </button>
                </form>
              </div>

              <div className="p-4 space-y-3">
                {doubts.length === 0 ? (
                  <div className="text-center text-sm text-slate-500 py-8">
                    No matching doubts found.
                  </div>
                ) : (
                  doubts.map((doubt) => {
                    const isOpen = expandedId === doubt.doubtId;
                    const isClosed = doubt.status === "Closed";

                    return (
                      <article
                        key={doubt.doubtId}
                        className="rounded-xl border border-slate-200 overflow-hidden bg-white"
                      >
                        <button
                          type="button"
                          onClick={() => setExpandedId(isOpen ? null : doubt.doubtId)}
                          className="w-full px-4 py-3 text-left flex items-center justify-between gap-3 hover:bg-slate-50/70"
                        >
                          <div className="min-w-0">
                            <div className="font-semibold text-sm text-slate-900">
                              #{doubt.doubtId} {doubt.title}
                            </div>
                            <div className="text-xs text-slate-500 mt-0.5">
                              {doubt.subjectName} ({doubt.subjectCode}) • By {doubt.authorDisplay}
                            </div>
                          </div>
                          <div className="flex items-center gap-2 shrink-0 flex-wrap justify-end">
                            {doubt.relevanceScore !== undefined && (
                              <span className="text-[11px] px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-medium">
                                Match: {doubt.relevanceScore}
                              </span>
                            )}
                            <span
                              className={`text-[11px] px-2 py-0.5 rounded font-medium ${
                                doubt.priority === "High"
                                  ? "bg-rose-50 text-rose-700 border border-rose-200"
                                  : doubt.priority === "Low"
                                    ? "bg-slate-100 text-slate-600"
                                    : "bg-blue-50 text-blue-700 border border-blue-200"
                              }`}
                            >
                              {doubt.priority}
                            </span>
                            <span
                              className={`text-[11px] px-2 py-0.5 rounded font-semibold ${
                                doubt.status.includes("Teacher")
                                  ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                                  : doubt.status.includes("Student")
                                    ? "bg-sky-50 text-sky-700 border border-sky-200"
                                    : doubt.status === "Closed"
                                      ? "bg-slate-200 text-slate-700"
                                      : "bg-amber-50 text-amber-700 border border-amber-200"
                              }`}
                            >
                              {doubt.status}
                            </span>
                            {isOpen ? (
                              <ChevronUp className="w-4 h-4 text-slate-400" />
                            ) : (
                              <ChevronDown className="w-4 h-4 text-slate-400" />
                            )}
                          </div>
                        </button>

                        {isOpen && (
                          <div className="px-4 pb-4 pt-2 border-t border-slate-100 space-y-3">
                            <div className="rounded-lg bg-slate-50 border border-slate-200/80 p-3 text-sm text-slate-800 space-y-2">
                              <p>{doubt.description || "No detailed description provided."}</p>
                              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                                <span>Faculty: {doubt.teacherName || "Unassigned"}</span>
                              </div>
                              {doubt.tags?.length > 0 && (
                                <div className="flex flex-wrap gap-1.5">
                                  {doubt.tags.map((t) => (
                                    <span
                                      key={t}
                                      className="text-[11px] px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-600"
                                    >
                                      #{t}
                                    </span>
                                  ))}
                                </div>
                              )}
                            </div>

                            {/* Verified Faculty Answers */}
                            {doubt.teacherAnswers?.map((ta) => (
                              <div
                                key={ta.teacherAnswerId}
                                className="rounded-lg border border-emerald-200 border-l-4 border-l-emerald-600 bg-emerald-50/60 p-3 flex items-start justify-between gap-3"
                              >
                                <div className="text-sm">
                                  <div className="font-semibold text-emerald-800 text-xs mb-1">
                                    Verified Faculty • {ta.teacherName}
                                  </div>
                                  <p className="text-slate-800">{ta.answerText}</p>
                                </div>
                                {user.role === "STUDENT" ? (
                                  <button
                                    type="button"
                                    onClick={() => handleUpvoteTeacher(ta.teacherAnswerId)}
                                    className="shrink-0 inline-flex items-center gap-1 rounded-full border border-emerald-300 bg-white px-2.5 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50"
                                  >
                                    <ThumbsUp className="w-3 h-3" /> {ta.upvotes || 0}
                                  </button>
                                ) : (
                                  <span className="shrink-0 text-xs text-emerald-700 font-medium">
                                    {ta.upvotes || 0} upvotes
                                  </span>
                                )}
                              </div>
                            ))}

                            {/* Student Peer Answers */}
                            {doubt.answers?.map((ans) => (
                              <div
                                key={ans.answerId}
                                className="rounded-lg border border-slate-200 border-l-4 border-l-blue-600 bg-white p-3 flex items-start justify-between gap-3"
                              >
                                <div className="text-sm">
                                  <div className="flex items-center gap-2 mb-1">
                                    <span className="font-semibold text-blue-700 text-xs">
                                      {ans.studentName || `Student #${ans.studentId}`}
                                    </span>
                                    {ans.isHelpful && (
                                      <span className="text-[11px] px-1.5 py-0.5 rounded bg-sky-50 text-sky-700 border border-sky-200 font-medium">
                                        Helpful Answer
                                      </span>
                                    )}
                                  </div>
                                  <p className="text-slate-800">{ans.answerText}</p>
                                </div>
                                <div className="flex items-center gap-1.5 shrink-0">
                                  {user.role === "STUDENT" ? (
                                    <button
                                      type="button"
                                      onClick={() => handleUpvotePeer(ans.answerId)}
                                      className="inline-flex items-center gap-1 rounded-full border border-blue-200 px-2.5 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50"
                                    >
                                      <ThumbsUp className="w-3 h-3" /> {ans.upvotes || 0}
                                    </button>
                                  ) : (
                                    <span className="text-xs text-slate-600 font-medium">
                                      {ans.upvotes || 0} upvotes
                                    </span>
                                  )}
                                  {(user.role === "TEACHER" || user.role === "ADMIN") && (
                                    <button
                                      type="button"
                                      onClick={() => handleToggleHelpful(ans.answerId, !ans.isHelpful)}
                                      title="Toggle Helpful"
                                      className="p-1.5 rounded border border-sky-200 text-sky-700 hover:bg-sky-50"
                                    >
                                      <Award className="w-3.5 h-3.5" />
                                    </button>
                                  )}
                                  {user.role === "ADMIN" && (
                                    <button
                                      type="button"
                                      onClick={() => handleSoftDeleteAnswer(ans.answerId)}
                                      title="Remove Answer"
                                      className="p-1.5 rounded border border-rose-200 text-rose-700 hover:bg-rose-50"
                                    >
                                      <Trash2 className="w-3.5 h-3.5" />
                                    </button>
                                  )}
                                </div>
                              </div>
                            ))}

                            {doubt.teacherAnswers?.length === 0 && doubt.answers?.length === 0 && (
                              <div className="text-xs text-slate-500 italic">No answers posted yet.</div>
                            )}

                            {/* Role Action Bar */}
                            {isClosed ? (
                              <div className="rounded-lg bg-slate-100 border border-slate-200 px-3 py-2 text-xs text-slate-600 flex items-center gap-1.5">
                                <Lock className="w-3.5 h-3.5" />
                                This doubt is closed and no longer accepts new answers.
                              </div>
                            ) : (
                              <>
                                {user.role === "STUDENT" && (
                                  <div className="flex gap-2 pt-2 border-t border-slate-100">
                                    <input
                                      type="text"
                                      value={answerDrafts[doubt.doubtId] || ""}
                                      onChange={(e) =>
                                        setAnswerDrafts((prev) => ({
                                          ...prev,
                                          [doubt.doubtId]: e.target.value,
                                        }))
                                      }
                                      placeholder="Write a peer answer..."
                                      className="flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
                                    />
                                    <button
                                      type="button"
                                      onClick={() => handlePostAnswer(doubt.doubtId, false)}
                                      className="rounded-lg bg-blue-600 hover:bg-blue-700 text-white px-3.5 py-1.5 text-xs font-semibold"
                                    >
                                      Post Answer
                                    </button>
                                  </div>
                                )}
                                {user.role === "TEACHER" && (
                                  <div className="space-y-2 pt-2 border-t border-slate-100">
                                    <div className="flex gap-2">
                                      <input
                                        type="text"
                                        value={answerDrafts[doubt.doubtId] || ""}
                                        onChange={(e) =>
                                          setAnswerDrafts((prev) => ({
                                            ...prev,
                                            [doubt.doubtId]: e.target.value,
                                          }))
                                        }
                                        placeholder="Write official faculty explanation..."
                                        className="flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
                                      />
                                      <button
                                        type="button"
                                        onClick={() => handlePostAnswer(doubt.doubtId, true)}
                                        className="rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white px-3.5 py-1.5 text-xs font-semibold"
                                      >
                                        Post Faculty Answer
                                      </button>
                                    </div>
                                    <button
                                      type="button"
                                      onClick={() => handleCloseDoubt(doubt.doubtId)}
                                      className="inline-flex items-center gap-1 rounded-lg border border-rose-300 text-rose-700 hover:bg-rose-50 px-3 py-1 text-xs font-medium"
                                    >
                                      <Lock className="w-3.5 h-3.5" /> Close Doubt
                                    </button>
                                  </div>
                                )}
                              </>
                            )}

                            {user.role === "ADMIN" && (
                              <div className="flex flex-wrap gap-2 pt-2 border-t border-slate-100">
                                {!isClosed && (
                                  <button
                                    type="button"
                                    onClick={() => handleCloseDoubt(doubt.doubtId)}
                                    className="inline-flex items-center gap-1 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50 px-3 py-1 text-xs font-medium"
                                  >
                                    <Lock className="w-3.5 h-3.5" /> Close
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => handleDeleteDoubt(doubt.doubtId, false)}
                                  className="inline-flex items-center gap-1 rounded-lg border border-amber-300 text-amber-800 hover:bg-amber-50 px-3 py-1 text-xs font-medium"
                                >
                                  <Archive className="w-3.5 h-3.5" /> Archive (Soft Delete)
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleDeleteDoubt(doubt.doubtId, true)}
                                  className="inline-flex items-center gap-1 rounded-lg border border-rose-300 text-rose-700 hover:bg-rose-50 px-3 py-1 text-xs font-medium"
                                >
                                  <Trash2 className="w-3.5 h-3.5" /> Delete Permanently
                                </button>
                              </div>
                            )}
                          </div>
                        )}
                      </article>
                    );
                  })
                )}
              </div>
            </section>

            {/* Admin Moderation Archive */}
            {user.role === "ADMIN" && (
              <section className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
                <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
                  <h3 className="font-semibold text-sm text-rose-700 flex items-center gap-2">
                    <ShieldAlert className="w-4 h-4" />
                    Moderation Archive
                  </h3>
                  <button
                    type="button"
                    onClick={loadAuditLog}
                    className="rounded border border-rose-200 px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50"
                  >
                    Refresh
                  </button>
                </div>
                <div className="p-4 space-y-2 text-xs">
                  {audit.deletedDoubts.length === 0 && audit.deletedAnswers.length === 0 ? (
                    <div className="text-slate-500">No archived entries.</div>
                  ) : (
                    <>
                      {audit.deletedDoubts.map((d) => (
                        <div
                          key={`dd-${d.doubtId}`}
                          className="rounded-lg border border-slate-200 bg-slate-50 p-3 flex items-center justify-between gap-2"
                        >
                          <div>
                            <div className="font-semibold text-slate-900">
                              Doubt #{d.doubtId}: {d.title}
                            </div>
                            <div className="text-slate-500">
                              Moderated by {d.adminName || `Admin #${d.deletedByAdminId}`} • Subject{" "}
                              {d.subjectCode}
                            </div>
                          </div>
                          <div className="flex gap-1.5 shrink-0">
                            <button
                              type="button"
                              onClick={() => handleRestoreDoubt(d.doubtId)}
                              className="inline-flex items-center gap-1 rounded border border-emerald-300 text-emerald-700 hover:bg-emerald-50 px-2.5 py-1 font-medium"
                            >
                              <RotateCcw className="w-3 h-3" /> Restore
                            </button>
                            <button
                              type="button"
                              onClick={() => handleDeleteDoubt(d.doubtId, true)}
                              className="inline-flex items-center gap-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50 px-2.5 py-1 font-medium"
                            >
                              <Trash2 className="w-3 h-3" /> Delete Permanently
                            </button>
                          </div>
                        </div>
                      ))}
                      {audit.deletedAnswers.map((a) => (
                        <div
                          key={`da-${a.answerId}`}
                          className="rounded-lg border border-slate-200 bg-slate-50 p-3"
                        >
                          <div className="font-semibold text-slate-900">
                            Peer Answer #{a.answerId} (Doubt #{a.doubtId})
                          </div>
                          <div className="text-slate-700 mt-0.5">{a.answerText}</div>
                          <div className="text-slate-500 mt-0.5">
                            Moderated by {a.adminName || `Admin #${a.deletedByAdminId}`}
                          </div>
                        </div>
                      ))}
                    </>
                  )}
                </div>
              </section>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
