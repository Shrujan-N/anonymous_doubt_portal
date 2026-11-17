"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { GraduationCap, LogIn, ShieldCheck, Sparkles, User, Users } from "lucide-react";

const TOKEN_KEY = "doubtPortalAuthToken";

type Role = "STUDENT" | "TEACHER" | "ADMIN";

export default function LoginPage() {
  const router = useRouter();
  const [role, setRole] = useState<Role>("STUDENT");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const checkSession = async () => {
      const token = sessionStorage.getItem(TOKEN_KEY);
      try {
        const res = await fetch("/api/auth/me", {
          credentials: "include",
          headers: token ? { "X-Auth-Token": token } : {},
        });
        if (res.ok) {
          router.replace("/dashboard");
        }
      } catch {
        // Backend may still be starting
      }
    };
    checkSession();
  }, [router]);

  async function submitLogin(loginEmail: string, loginPassword: string, loginRole: Role) {
    setError("");
    setLoading(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          email: loginEmail.trim(),
          password: loginPassword,
          role: loginRole,
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || "Invalid credentials.");
      }
      if (data.token) {
        sessionStorage.setItem(TOKEN_KEY, data.token);
      }
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to sign in.");
    } finally {
      setLoading(false);
    }
  }

  function handleFormSubmit(e: React.FormEvent) {
    e.preventDefault();
    submitLogin(email, password, role);
  }

  function handleDemoLogin(demoEmail: string, demoPwd: string, demoRole: Role) {
    setRole(demoRole);
    setEmail(demoEmail);
    setPassword(demoPwd);
    submitLogin(demoEmail, demoPwd, demoRole);
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <header className="bg-slate-900 text-white border-b border-slate-800">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5 font-bold text-lg">
            <GraduationCap className="w-6 h-6 text-blue-400" />
            <span>Anonymous Classroom Doubt Portal</span>
          </div>
          <span className="text-xs px-2.5 py-1 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
            PES University • DBMS Level 3
          </span>
        </div>
      </header>

      <main className="flex-1 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div className="px-6 py-5 border-b border-slate-100 bg-white">
            <h1 className="text-xl font-bold text-slate-900">Sign in to Portal</h1>
            <p className="text-sm text-slate-500 mt-0.5">
              Role-based access for Students, Faculty, and Admin Moderators
            </p>
          </div>

          <div className="p-6">
            {error && (
              <div className="mb-4 rounded-lg bg-red-50 border border-red-200 px-3.5 py-2.5 text-sm text-red-700">
                {error}
              </div>
            )}

            <div className="grid grid-cols-3 gap-1.5 bg-slate-100 p-1 rounded-lg mb-4">
              {(["STUDENT", "TEACHER", "ADMIN"] as Role[]).map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRole(r)}
                  className={`py-1.5 text-xs font-semibold rounded-md transition ${
                    role === r
                      ? "bg-white text-blue-700 shadow-sm"
                      : "text-slate-600 hover:text-slate-900"
                  }`}
                >
                  {r === "STUDENT" ? "Student" : r === "TEACHER" ? "Faculty" : "Admin"}
                </button>
              ))}
            </div>

            <form onSubmit={handleFormSubmit} className="space-y-3.5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="email">
                  University Email
                </label>
                <input
                  id="email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="e.g., rahul@pes.edu"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1" htmlFor="password">
                  Password
                </label>
                <input
                  id="password"
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter password"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full flex items-center justify-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2.5 text-sm transition disabled:opacity-60"
              >
                <LogIn className="w-4 h-4" />
                {loading ? "Signing in..." : "Sign In"}
              </button>
            </form>

            <div className="mt-6 pt-5 border-t border-slate-100">
              <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500 mb-2.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                Quick Viva Demo Login
              </div>

              <div className="space-y-2">
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => handleDemoLogin("rahul@pes.edu", "hashed_pwd_1", "STUDENT")}
                    className="flex items-center justify-center gap-1 rounded-md border border-blue-200 bg-blue-50/70 hover:bg-blue-100 text-blue-700 py-1.5 px-2 text-xs font-medium"
                  >
                    <User className="w-3.5 h-3.5" /> Rahul
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDemoLogin("ananya@pes.edu", "hashed_pwd_2", "STUDENT")}
                    className="flex items-center justify-center gap-1 rounded-md border border-blue-200 bg-blue-50/70 hover:bg-blue-100 text-blue-700 py-1.5 px-2 text-xs font-medium"
                  >
                    <User className="w-3.5 h-3.5" /> Ananya
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDemoLogin("vikram@pes.edu", "hashed_pwd_3", "STUDENT")}
                    className="flex items-center justify-center gap-1 rounded-md border border-blue-200 bg-blue-50/70 hover:bg-blue-100 text-blue-700 py-1.5 px-2 text-xs font-medium"
                  >
                    <User className="w-3.5 h-3.5" /> Vikram
                  </button>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => handleDemoLogin("sudhir@pes.edu", "prof_pwd_1", "TEACHER")}
                    className="flex items-center justify-center gap-1.5 rounded-md border border-emerald-200 bg-emerald-50/70 hover:bg-emerald-100 text-emerald-700 py-1.5 px-2 text-xs font-medium"
                  >
                    <Users className="w-3.5 h-3.5" /> Faculty (Dr. Sudhir)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDemoLogin("admin@pes.edu", "admin_root_pwd", "ADMIN")}
                    className="flex items-center justify-center gap-1.5 rounded-md border border-rose-200 bg-rose-50/70 hover:bg-rose-100 text-rose-700 py-1.5 px-2 text-xs font-medium"
                  >
                    <ShieldCheck className="w-3.5 h-3.5" /> Admin (Root)
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
