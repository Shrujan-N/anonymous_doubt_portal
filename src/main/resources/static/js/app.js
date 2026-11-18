const TOKEN_KEY = "doubtPortalAuthToken";
let currentUser = null;

function escapeHtml(value) {
    if (value === null || value === undefined) return "";
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function authHeaders(extra = {}) {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const headers = { ...extra };
    if (token) {
        headers["X-Auth-Token"] = token;
    }
    return headers;
}

async function apiFetch(url, options = {}) {
    const res = await fetch(url, {
        ...options,
        credentials: "include",
        headers: authHeaders(options.headers || {})
    });
    if (res.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
        window.location.href = "/";
        throw new Error("Session expired. Redirecting to login.");
    }
    return res;
}

function showError(message) {
    const alert = document.getElementById("errorAlert");
    document.getElementById("errorAlertText").textContent = message;
    alert.classList.remove("d-none");
    document.getElementById("successAlert").classList.add("d-none");
}

function showSuccess(message) {
    const alert = document.getElementById("successAlert");
    document.getElementById("successAlertText").textContent = message;
    alert.classList.remove("d-none");
    document.getElementById("errorAlert").classList.add("d-none");
    setTimeout(() => alert.classList.add("d-none"), 4000);
}

function clearAlerts() {
    document.getElementById("errorAlert").classList.add("d-none");
}

async function loadSessionUser() {
    try {
        const res = await apiFetch("/api/auth/me");
        if (!res.ok) {
            window.location.href = "/";
            return false;
        }
        currentUser = await res.json();
        applyRoleUI();
        return true;
    } catch (_) {
        return false;
    }
}

function applyRoleUI() {
    const role = currentUser.role;
    const userInfo = document.getElementById("userInfo");
    const roleAlert = document.getElementById("roleAlert");
    const roleAlertText = document.getElementById("roleAlertText");
    const postDoubtCard = document.getElementById("postDoubtCard");
    const vectorCreateBox = document.getElementById("vectorCreateBox");
    const adminAuditCard = document.getElementById("adminAuditCard");

    if (role === "STUDENT") {
        userInfo.className = "badge bg-primary px-3 py-2";
        userInfo.textContent = `Student: ${currentUser.name} (${currentUser.detail || "PES"})`;
        roleAlert.className = "alert alert-primary shadow-sm d-flex align-items-center gap-2";
        roleAlertText.innerHTML = `Active Mode: <strong>Student View</strong> — Post doubts (anonymously if preferred), write peer answers, and upvote solutions.`;
        postDoubtCard.style.display = "block";
        vectorCreateBox.classList.add("d-none");
        adminAuditCard.classList.add("d-none");
    } else if (role === "TEACHER") {
        userInfo.className = "badge bg-success px-3 py-2";
        userInfo.textContent = `Faculty: ${currentUser.name} (${currentUser.detail || "Faculty"})`;
        roleAlert.className = "alert alert-warning shadow-sm d-flex align-items-center gap-2";
        roleAlertText.innerHTML = `Active Mode: <strong>Teacher View</strong> — Verify peer answers, post official explanations, close doubts, and curate topic tags.`;
        postDoubtCard.style.display = "none";
        vectorCreateBox.classList.remove("d-none");
        adminAuditCard.classList.add("d-none");
    } else {
        userInfo.className = "badge bg-danger px-3 py-2";
        userInfo.textContent = `Admin: ${currentUser.name}`;
        roleAlert.className = "alert alert-secondary shadow-sm d-flex align-items-center gap-2";
        roleAlertText.innerHTML = `Active Mode: <strong>Admin Moderation View</strong> — Moderate archived threads, restore or permanently delete posts, and manage topic indexes.`;
        postDoubtCard.style.display = "none";
        vectorCreateBox.classList.remove("d-none");
        adminAuditCard.classList.remove("d-none");
        loadAuditLog();
    }
}

async function logout() {
    await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: authHeaders()
    }).catch(() => {});
    sessionStorage.removeItem(TOKEN_KEY);
    window.location.href = "/";
}

async function loadSubjects() {
    try {
        const res = await apiFetch("/api/subjects");
        const subjects = await res.json();
        const formSelect = document.getElementById("subjectId");
        const filterSelect = document.getElementById("filterSubject");

        formSelect.innerHTML = subjects.map(s =>
            `<option value="${Number(s.subjectId)}">${escapeHtml(s.subjectName)} (${escapeHtml(s.subjectCode)})</option>`
        ).join("");
        filterSelect.innerHTML = `<option value="">All Subjects</option>` + subjects.map(s =>
            `<option value="${Number(s.subjectId)}">${escapeHtml(s.subjectCode)} — ${escapeHtml(s.subjectName)}</option>`
        ).join("");
    } catch (_) {}
}

function renderStatusBadge(status) {
    const currentStatus = status || "Pending";
    if (currentStatus.includes("Teacher")) {
        return '<span class="badge bg-success-subtle text-success border border-success-subtle px-2 py-1"><i class="bi bi-patch-check-fill me-1"></i>Resolved by Faculty</span>';
    }
    if (currentStatus.includes("Student")) {
        return '<span class="badge bg-info-subtle text-info-emphasis border border-info-subtle px-2 py-1"><i class="bi bi-people-fill me-1"></i>Peer Answered</span>';
    }
    if (currentStatus === "Closed") {
        return '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle px-2 py-1"><i class="bi bi-lock-fill me-1"></i>Closed</span>';
    }
    return '<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle px-2 py-1"><i class="bi bi-hourglass-split me-1"></i>Pending</span>';
}

function renderPriorityBadge(priority) {
    const p = priority || "Medium";
    if (p === "High") {
        return '<span class="badge bg-danger-subtle text-danger border border-danger-subtle">High Priority</span>';
    }
    if (p === "Low") {
        return '<span class="badge bg-light text-muted border">Low Priority</span>';
    }
    return '<span class="badge bg-primary-subtle text-primary border border-primary-subtle">Medium Priority</span>';
}

async function loadAllDoubts() {
    clearAlerts();
    const container = document.getElementById("doubtsContainer");
    const params = new URLSearchParams();
    const subjectId = document.getElementById("filterSubject").value;
    const status = document.getElementById("filterStatus").value;
    const q = document.getElementById("fullTextInput").value.trim();
    if (subjectId) params.set("subjectId", subjectId);
    if (status) params.set("status", status);
    if (q) params.set("q", q);

    try {
        const response = await apiFetch("/api/doubts" + (params.toString() ? `?${params.toString()}` : ""));
        if (!response.ok) throw new Error("Unable to load doubts.");
        const doubts = await response.json();
        container.innerHTML = "";

        if (doubts.length === 0) {
            container.innerHTML = '<div class="text-center text-muted py-4"><i class="bi bi-inbox fs-3 d-block mb-2"></i>No matching questions found.</div>';
            return;
        }

        const role = currentUser ? currentUser.role : "STUDENT";

        doubts.forEach((doubt, index) => {
            const safeId = Number(doubt.doubtId);
            const isClosed = doubt.status === "Closed";
            const statusBadgeHtml = renderStatusBadge(doubt.status);
            const priorityBadgeHtml = renderPriorityBadge(doubt.priority);
            const authorText = doubt.isAnonymous
                ? '<span class="text-muted"><i class="bi bi-incognito"></i> Anonymous Student</span>'
                : `<span class="text-secondary"><i class="bi bi-person"></i> ${escapeHtml(doubt.authorDisplay)}</span>`;
            const subjectLabel = doubt.subjectName
                ? `${escapeHtml(doubt.subjectName)} (${escapeHtml(doubt.subjectCode)})`
                : `Subject #${Number(doubt.subjectId)}`;
            const tagsHtml = (doubt.tags || []).map(t =>
                `<span class="badge bg-secondary-subtle text-secondary border me-1">${escapeHtml(t)}</span>`
            ).join("");

            const teacherAnswers = doubt.teacherAnswers || [];
            const teacherAnswersHtml = teacherAnswers.map(ta => `
                <div class="p-3 mb-2 rounded border answer-card-teacher d-flex justify-content-between align-items-start gap-2">
                    <div>
                        <div class="d-flex align-items-center gap-2 mb-1">
                            <strong class="text-success"><i class="bi bi-patch-check-fill"></i> Faculty: ${escapeHtml(ta.teacherName || ("Teacher #" + ta.teacherId))}</strong>
                            <span class="badge bg-success">Verified</span>
                        </div>
                        <p class="mb-0 small text-dark">${escapeHtml(ta.answerText)}</p>
                    </div>
                    ${role === "STUDENT" ? `
                        <button class="btn btn-sm btn-outline-success upvote-btn text-nowrap" onclick="upvoteTeacherAnswer(${Number(ta.teacherAnswerId)})">
                            <i class="bi bi-hand-thumbs-up-fill"></i> ${Number(ta.upvotes || 0)}
                        </button>
                    ` : `<span class="badge bg-success-subtle text-success border text-nowrap">${Number(ta.upvotes || 0)} upvotes</span>`}
                </div>
            `).join("");

            const studentAnswers = doubt.answers || [];
            const studentAnswersHtml = studentAnswers.map(a => `
                <div class="p-3 mb-2 rounded border answer-card-student d-flex justify-content-between align-items-start gap-2">
                    <div>
                        <div class="d-flex align-items-center gap-1 mb-1 flex-wrap">
                            <strong class="text-primary small">${escapeHtml(a.studentName || ("Student #" + a.studentId))}</strong>
                            ${a.isHelpful ? '<span class="badge bg-info-subtle text-info border border-info-subtle badge-tag">Marked Helpful</span>' : ''}
                        </div>
                        <p class="mb-0 small text-dark">${escapeHtml(a.answerText)}</p>
                    </div>
                    <div class="d-flex align-items-center gap-1 flex-shrink-0">
                        ${role === "STUDENT" ? `
                            <button class="btn btn-sm btn-outline-primary upvote-btn text-nowrap" onclick="upvoteAnswer(${Number(a.answerId)})">
                                <i class="bi bi-hand-thumbs-up-fill"></i> ${Number(a.upvotes || 0)}
                            </button>
                        ` : `<span class="badge bg-primary-subtle text-primary border">${Number(a.upvotes || 0)} upvotes</span>`}
                        ${(role === "TEACHER" || role === "ADMIN") ? `
                            <button class="btn btn-sm btn-outline-info" onclick="toggleHelpful(${Number(a.answerId)}, ${!a.isHelpful})" title="Toggle Helpful">
                                <i class="bi bi-award"></i>
                            </button>
                        ` : ""}
                        ${role === "ADMIN" ? `
                            <button class="btn btn-sm btn-outline-danger" onclick="softDeleteAnswer(${Number(a.answerId)})" title="Remove Answer">
                                <i class="bi bi-trash"></i>
                            </button>
                        ` : ""}
                    </div>
                </div>
            `).join("");

            let actionAreaHtml = "";
            if (isClosed) {
                actionAreaHtml += '<div class="alert alert-secondary py-2 px-3 small mb-2 mt-3"><i class="bi bi-lock-fill me-1"></i> This doubt is closed and no longer accepts new answers.</div>';
            } else if (role === "STUDENT") {
                actionAreaHtml += `
                    <div class="mt-3 pt-3 border-top">
                        <div class="input-group input-group-sm">
                            <input type="text" id="studentAnswerInput-${safeId}" class="form-control" placeholder="Write a peer answer...">
                            <button class="btn btn-primary" type="button" onclick="submitStudentAnswer(${safeId})">
                                <i class="bi bi-reply-fill"></i> Post Answer
                            </button>
                        </div>
                    </div>
                `;
            } else if (role === "TEACHER") {
                actionAreaHtml += `
                    <div class="mt-3 pt-3 border-top">
                        <div class="input-group input-group-sm mb-2">
                            <input type="text" id="teacherAnswerInput-${safeId}" class="form-control" placeholder="Write official faculty explanation...">
                            <button class="btn btn-success" type="button" onclick="submitTeacherAnswer(${safeId})">
                                <i class="bi bi-check2-circle"></i> Post Faculty Answer
                            </button>
                        </div>
                        <button class="btn btn-sm btn-outline-danger" onclick="closeDoubt(${safeId})">
                            <i class="bi bi-lock-fill"></i> Close Doubt
                        </button>
                    </div>
                `;
            }

            if (role === "ADMIN") {
                actionAreaHtml += `
                    <div class="mt-3 pt-3 border-top d-flex flex-wrap gap-2">
                        ${!isClosed ? `<button class="btn btn-sm btn-outline-secondary" onclick="closeDoubt(${safeId})"><i class="bi bi-lock"></i> Close Doubt</button>` : ""}
                        <button class="btn btn-sm btn-outline-warning" onclick="deleteDoubt(${safeId}, false)"><i class="bi bi-archive"></i> Archive (Soft Delete)</button>
                        <button class="btn btn-sm btn-outline-danger" onclick="deleteDoubt(${safeId}, true)"><i class="bi bi-trash-fill"></i> Delete Permanently</button>
                    </div>
                `;
            }

            container.insertAdjacentHTML("beforeend", `
                <div class="accordion-item shadow-sm">
                    <h2 class="accordion-header" id="heading${safeId}">
                        <button class="accordion-button ${index !== 0 ? "collapsed" : ""}" type="button"
                                data-bs-toggle="collapse" data-bs-target="#collapse${safeId}">
                            <div class="d-flex w-100 justify-content-between align-items-center me-3 flex-wrap gap-2">
                                <div>
                                    <span class="fw-bold text-dark me-2">#${safeId} ${escapeHtml(doubt.title)}</span>
                                    <small class="text-muted">[${subjectLabel}]</small>
                                </div>
                                <div class="d-flex align-items-center gap-2">
                                    ${doubt.relevanceScore ? `<span class="badge bg-dark-subtle text-dark">Match: ${escapeHtml(doubt.relevanceScore)}</span>` : ""}
                                    ${priorityBadgeHtml}
                                    ${statusBadgeHtml}
                                </div>
                            </div>
                        </button>
                    </h2>
                    <div id="collapse${safeId}" class="accordion-collapse collapse ${index === 0 ? "show" : ""}">
                        <div class="accordion-body">
                            <div class="p-3 bg-light rounded border mb-3">
                                <p class="mb-2 text-dark">${escapeHtml(doubt.description || "No further details provided.")}</p>
                                <div class="small text-muted d-flex flex-wrap align-items-center justify-content-between gap-2 mb-2">
                                    <div class="d-flex flex-wrap align-items-center gap-3">
                                        ${authorText}
                                        <span><i class="bi bi-person-workspace"></i> Faculty: ${escapeHtml(doubt.teacherName || "Unassigned")}</span>
                                    </div>
                                    <button type="button" class="btn btn-link btn-sm p-0 text-decoration-none" onclick="toggleRelatedDoubts(${safeId})">
                                        <i class="bi bi-diagram-2"></i> Related Questions
                                    </button>
                                </div>
                                ${tagsHtml ? `<div>${tagsHtml}</div>` : ""}
                            </div>
                            <div id="relatedBox-${safeId}" class="d-none alert alert-info py-2 px-3 small mb-3"></div>
                            <h6 class="small fw-bold text-muted text-uppercase mb-2">Answers &amp; Solutions</h6>
                            ${teacherAnswersHtml}
                            ${studentAnswersHtml}
                            ${teacherAnswers.length === 0 && studentAnswers.length === 0 ? '<div class="small text-muted fst-italic py-2"><i class="bi bi-chat-dots me-1"></i>No answers yet.</div>' : ''}
                            ${actionAreaHtml}
                        </div>
                    </div>
                </div>
            `);
        });
    } catch (error) {
        container.innerHTML = `<div class="text-center text-danger py-4">${escapeHtml(error.message)}</div>`;
        showError(error.message);
    }
}

async function toggleRelatedDoubts(doubtId) {
    const box = document.getElementById(`relatedBox-${doubtId}`);
    if (!box) return;
    if (!box.classList.contains("d-none")) {
        box.classList.add("d-none");
        return;
    }
    try {
        const res = await apiFetch(`/api/doubts/${doubtId}/related`);
        if (!res.ok) throw new Error("Unable to fetch related discussions.");
        const rows = await res.json();
        if (rows.length === 0) {
            box.innerHTML = "No related discussions found.";
        } else {
            box.innerHTML = `<div class="fw-semibold mb-1">Related Discussions by Topic</div>` +
                rows.map(r => `<div>&bull; #${Number(r.doubtId)} ${escapeHtml(r.title)} (${Number(r.minTagHops)} ${Number(r.minTagHops) === 1 ? "hop" : "hops"} away)</div>`).join("");
        }
        box.classList.remove("d-none");
    } catch (err) {
        showError(err.message);
    }
}

async function submitStudentAnswer(doubtId) {
    const input = document.getElementById(`studentAnswerInput-${doubtId}`);
    const answerText = input ? input.value.trim() : "";
    if (!answerText) return showError("Please enter an answer.");
    try {
        const res = await apiFetch(`/api/doubts/${doubtId}/answers`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ answerText })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to submit peer answer.");
        showSuccess(data.message);
        await loadAllDoubts();
        await loadLeaderboard();
    } catch (err) {
        showError(err.message);
    }
}

async function submitTeacherAnswer(doubtId) {
    const input = document.getElementById(`teacherAnswerInput-${doubtId}`);
    const answerText = input ? input.value.trim() : "";
    if (!answerText) return showError("Please enter an official faculty answer.");
    try {
        const res = await apiFetch(`/api/doubts/${doubtId}/teacher-answers`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ answerText })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to submit faculty answer.");
        showSuccess(data.message);
        await loadAllDoubts();
    } catch (err) {
        showError(err.message);
    }
}

async function upvoteAnswer(answerId) {
    try {
        const res = await apiFetch(`/api/doubts/answers/${answerId}/upvote`, { method: "POST" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to upvote.");
        showSuccess(data.message);
        await loadAllDoubts();
        await loadLeaderboard();
    } catch (err) {
        showError(err.message);
    }
}

async function upvoteTeacherAnswer(teacherAnswerId) {
    try {
        const res = await apiFetch(`/api/doubts/teacher-answers/${teacherAnswerId}/upvote`, { method: "POST" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to upvote faculty answer.");
        showSuccess(data.message);
        await loadAllDoubts();
    } catch (err) {
        showError(err.message);
    }
}

async function toggleHelpful(answerId, helpful) {
    try {
        const res = await apiFetch(`/api/doubts/answers/${answerId}/helpful?helpful=${helpful}`, { method: "PUT" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to update helpful flag.");
        showSuccess(data.message);
        await loadAllDoubts();
        await loadLeaderboard();
    } catch (err) {
        showError(err.message);
    }
}

async function closeDoubt(doubtId) {
    try {
        const res = await apiFetch(`/api/doubts/${doubtId}/close`, { method: "PUT" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to close doubt.");
        showSuccess(data.message);
        await loadAllDoubts();
    } catch (err) {
        showError(err.message);
    }
}

async function deleteDoubt(doubtId, hard = false) {
    if (hard && !confirm(`Permanently delete Doubt #${doubtId}? This will also remove all its answers, upvotes, and tags.`)) {
        return;
    }
    try {
        const res = await apiFetch(`/api/doubts/${doubtId}?hard=${hard}`, { method: "DELETE" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to delete doubt.");
        showSuccess(data.message);
        await loadAllDoubts();
        await searchVectorsSemantic();
        await loadAuditLog();
    } catch (err) {
        showError(err.message);
    }
}

async function softDeleteAnswer(answerId) {
    try {
        const res = await apiFetch(`/api/doubts/answers/${answerId}`, { method: "DELETE" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to moderate answer.");
        showSuccess(data.message);
        await loadAllDoubts();
        await loadLeaderboard();
        await loadAuditLog();
    } catch (err) {
        showError(err.message);
    }
}

async function restoreDoubt(doubtId) {
    try {
        const res = await apiFetch(`/api/doubts/${doubtId}/restore`, { method: "PUT" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to restore doubt.");
        showSuccess(data.message);
        await loadAllDoubts();
        await searchVectorsSemantic();
        await loadAuditLog();
    } catch (err) {
        showError(err.message);
    }
}

async function loadAuditLog() {
    if (!currentUser || currentUser.role !== "ADMIN") return;
    const container = document.getElementById("auditContainer");
    try {
        const res = await apiFetch("/api/doubts/audit");
        const data = await res.json();
        const doubts = data.deletedDoubts || [];
        const answers = data.deletedAnswers || [];
        if (doubts.length === 0 && answers.length === 0) {
            container.innerHTML = '<div class="text-muted small">No archived entries.</div>';
            return;
        }
        container.innerHTML = `
            ${doubts.map(d => `
                <div class="border rounded p-2 mb-2 d-flex justify-content-between align-items-center gap-2 bg-light">
                    <div class="small">
                        <strong>Doubt #${Number(d.doubtId)}:</strong> ${escapeHtml(d.title)}
                        <div class="text-muted">Moderated by Admin: ${escapeHtml(d.adminName || ("#" + d.deletedByAdminId))}</div>
                    </div>
                    <div class="d-flex gap-1">
                        <button class="btn btn-sm btn-outline-success" onclick="restoreDoubt(${Number(d.doubtId)})">Restore</button>
                        <button class="btn btn-sm btn-outline-danger" onclick="deleteDoubt(${Number(d.doubtId)}, true)">Delete Permanently</button>
                    </div>
                </div>
            `).join("")}
            ${answers.map(a => `
                <div class="border rounded p-2 mb-2 bg-light small">
                    <strong>Answer #${Number(a.answerId)}</strong> (on Doubt #${Number(a.doubtId)}): ${escapeHtml(a.answerText)}
                    <div class="text-muted">Moderated by Admin: ${escapeHtml(a.adminName || ("#" + a.deletedByAdminId))}</div>
                </div>
            `).join("")}
        `;
    } catch (err) {
        container.innerHTML = `<div class="text-danger small">${escapeHtml(err.message)}</div>`;
    }
}

async function searchVectorsSemantic() {
    const q = document.getElementById("vectorQueryInput").value.trim();
    const url = q ? `/api/nosql/vectors/semantic?q=${encodeURIComponent(q)}` : `/api/nosql/vectors`;
    await renderVectorResults(url, q ? `Similar Topics for "${q}"` : "Indexed Topics");
}

async function searchVectorsByTag() {
    const tag = document.getElementById("vectorQueryInput").value.trim();
    const url = tag ? `/api/nosql/vectors/search?tag=${encodeURIComponent(tag)}` : `/api/nosql/vectors`;
    await renderVectorResults(url, tag ? `Tag Matches for "${tag}"` : "Indexed Topics");
}

async function renderVectorResults(url, heading) {
    const resultsDiv = document.getElementById("vectorSearchResults");
    const canEdit = currentUser && (currentUser.role === "TEACHER" || currentUser.role === "ADMIN");
    try {
        const response = await apiFetch(url);
        if (!response.ok) throw new Error("Unable to load topic index.");
        const documents = await response.json();

        if (documents.length === 0) {
            resultsDiv.innerHTML = `<div class="text-muted fst-italic">No matching topics found.</div>`;
            return;
        }

        resultsDiv.innerHTML = `
            <div class="fw-medium text-dark mb-2">${escapeHtml(heading)} (${documents.length}):</div>
            ${documents.map(doc => {
                const safeDocId = escapeHtml(doc.id);
                const simPct = doc.similarityScore !== null && doc.similarityScore !== undefined
                    ? `${Math.round(doc.similarityScore * 100)}% match`
                    : "Indexed";
                return `
                    <div class="border rounded p-2 mb-2 bg-light">
                        <div class="d-flex justify-content-between align-items-start gap-2">
                            <div class="fw-semibold text-dark">${doc.doubtId ? `#${Number(doc.doubtId)}: ` : "Topic: "}${escapeHtml(doc.title)}</div>
                            <span class="badge bg-success-subtle text-success border border-success-subtle text-nowrap">${escapeHtml(simPct)}</span>
                        </div>
                        <div class="mt-1">${(doc.tags || []).map(t => `<span class="badge bg-secondary-subtle text-secondary me-1">${escapeHtml(t)}</span>`).join("")}</div>
                        ${canEdit ? `
                            <div class="mt-2 d-flex gap-1">
                                <button class="btn btn-xs btn-outline-primary py-0 px-2 vec-edit-btn" style="font-size:0.75rem" data-id="${safeDocId}" data-tags="${escapeHtml((doc.tags || []).join(', '))}">Edit Tags</button>
                                <button class="btn btn-xs btn-outline-danger py-0 px-2 vec-delete-btn" style="font-size:0.75rem" data-id="${safeDocId}">Delete</button>
                            </div>
                        ` : ""}
                    </div>
                `;
            }).join("")}
        `;
        resultsDiv.querySelectorAll(".vec-edit-btn").forEach(btn => {
            btn.addEventListener("click", () => promptEditVectorTags(btn.dataset.id, btn.dataset.tags || ""));
        });
        resultsDiv.querySelectorAll(".vec-delete-btn").forEach(btn => {
            btn.addEventListener("click", () => deleteVectorDoc(btn.dataset.id));
        });
    } catch (error) {
        showError(error.message);
    }
}

async function createVectorDocument() {
    const rawDoubtId = document.getElementById("newVecDoubtId").value.trim();
    const parsedDoubtId = rawDoubtId ? parseInt(rawDoubtId, 10) : null;
    const doubtId = parsedDoubtId && parsedDoubtId > 0 ? parsedDoubtId : null;
    const title = document.getElementById("newVecTitle").value.trim();
    const tagsRaw = document.getElementById("newVecTags").value;
    const tags = tagsRaw.split(",").map(s => s.trim()).filter(Boolean);
    if (!title) return showError("Enter a title for the topic entry.");
    try {
        const res = await apiFetch("/api/nosql/vectors", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ doubtId, title, tags })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to add topic entry.");
        document.getElementById("newVecDoubtId").value = "";
        document.getElementById("newVecTitle").value = "";
        document.getElementById("newVecTags").value = "";
        showSuccess("Topic entry added to search index!");
        await searchVectorsSemantic();
    } catch (err) {
        showError(err.message);
    }
}

async function promptEditVectorTags(id, currentTagsCsv) {
    const updated = prompt("Update comma-separated topic tags:", currentTagsCsv);
    if (updated === null) return;
    const tags = updated.split(",").map(s => s.trim()).filter(Boolean);
    try {
        const res = await apiFetch(`/api/nosql/vectors/${encodeURIComponent(id)}/tags`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(tags)
        });
        if (!res.ok) throw new Error("Unable to update topic tags.");
        showSuccess("Topic tags updated!");
        await searchVectorsSemantic();
    } catch (err) {
        showError(err.message);
    }
}

async function deleteVectorDoc(id) {
    try {
        const res = await apiFetch(`/api/nosql/vectors/${encodeURIComponent(id)}`, { method: "DELETE" });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || "Unable to remove topic entry.");
        showSuccess(data.message);
        await searchVectorsSemantic();
    } catch (err) {
        showError(err.message);
    }
}

async function loadLeaderboard() {
    const container = document.getElementById("leaderboardContainer");
    try {
        const response = await apiFetch("/api/doubts/leaderboard");
        if (!response.ok) throw new Error("Unable to load leaderboard.");
        const rows = await response.json();
        container.innerHTML = rows.map((r, i) => `
            <div class="list-group-item d-flex justify-content-between align-items-center py-2 px-3">
                <div>
                    <strong class="text-dark">#${i + 1} ${escapeHtml(r.name)}</strong> <span class="text-muted">(${escapeHtml(r.branch)})</span>
                    <div class="text-muted" style="font-size: 0.75rem;">${Number(r.totalAnswers)} answers &bull; ${Number(r.totalUpvotes)} upvotes</div>
                </div>
                <span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-3 py-2 fw-bold">${Number(r.score)} pts</span>
            </div>
        `).join("");
    } catch (_) {
        container.innerHTML = '<div class="p-3 text-muted text-center">Leaderboard unavailable</div>';
    }
}

document.getElementById("doubtForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const tags = document.getElementById("tagsInput").value
        .split(",").map(s => s.trim()).filter(Boolean);
    try {
        const response = await apiFetch("/api/doubts", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                subjectId: parseInt(document.getElementById("subjectId").value, 10),
                title: document.getElementById("title").value.trim(),
                description: document.getElementById("description").value.trim(),
                isAnonymous: document.getElementById("isAnonymous").checked,
                priority: document.getElementById("priority").value,
                tags
            })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || "Unable to post question.");
        event.target.reset();
        showSuccess("Question submitted successfully!");
        await loadAllDoubts();
        await searchVectorsSemantic();
    } catch (error) {
        showError(error.message);
    }
});

(async function init() {
    const ok = await loadSessionUser();
    if (!ok) return;
    document.getElementById("fullTextInput")?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") { e.preventDefault(); loadAllDoubts(); }
    });
    document.getElementById("vectorQueryInput")?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") { e.preventDefault(); searchVectorsSemantic(); }
    });
    await loadSubjects();
    await loadAllDoubts();
    await loadLeaderboard();
    await searchVectorsSemantic();
})();
