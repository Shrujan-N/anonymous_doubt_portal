let currentRole = "STUDENT";
let currentUserId = 1;
const studentNames = {
    1: "Rahul Sharma #1",
    2: "Ananya Rao #2",
    3: "Vikram Verma #3"
};

function showError(message) {
    const alert = document.getElementById("errorAlert");
    alert.textContent = message;
    alert.classList.remove("d-none");
    document.getElementById("successAlert").classList.add("d-none");
}

function showSuccess(message) {
    const alert = document.getElementById("successAlert");
    alert.textContent = message;
    alert.classList.remove("d-none");
    document.getElementById("errorAlert").classList.add("d-none");
    setTimeout(() => alert.classList.add("d-none"), 4000);
}

function clearAlerts() {
    document.getElementById("errorAlert").classList.add("d-none");
}

function switchStudentUser(studentId) {
    currentUserId = parseInt(studentId, 10) || 1;
    updateNavbarUserInfo();
}

function updateNavbarUserInfo() {
    const userInfo = document.getElementById("userInfo");
    const studentSelect = document.getElementById("studentSelector");
    if (currentRole === "STUDENT") {
        studentSelect.style.display = "inline-block";
        userInfo.innerText = `Role: STUDENT (${studentNames[currentUserId]})`;
    } else if (currentRole === "TEACHER") {
        studentSelect.style.display = "none";
        userInfo.innerText = "Role: TEACHER (Dr. Sudhir Kumar #1)";
    } else {
        studentSelect.style.display = "none";
        userInfo.innerText = "Role: ADMIN (System Admin #1)";
    }
}

function switchRole(role) {
    currentRole = role;
    clearAlerts();
    updateNavbarUserInfo();

    const alert = document.getElementById("roleAlert");
    const postCard = document.getElementById("postDoubtCard");

    if (role === "STUDENT") {
        alert.className = "alert alert-info shadow-sm";
        alert.innerHTML = "Active Mode: <strong>Student View</strong> (Ask doubts, post peer answers &amp; upvote answers)";
        postCard.style.display = "block";
    } else if (role === "TEACHER") {
        alert.className = "alert alert-warning shadow-sm";
        alert.innerHTML = "Active Mode: <strong>Teacher View</strong> (Post official faculty answers &amp; close resolved doubts)";
        postCard.style.display = "none";
    } else {
        alert.className = "alert alert-danger shadow-sm";
        alert.innerHTML = "Active Mode: <strong>Admin View</strong> (Moderate doubts, close threads &amp; manage NoSQL vector index)";
        postCard.style.display = "none";
    }
    loadAllDoubts();
}

function getStatusBadgeClass(status) {
    if (!status) return "secondary";
    if (status.includes("Teacher")) return "success";
    if (status.includes("Student")) return "info text-dark";
    if (status === "Closed") return "dark";
    return "warning text-dark";
}

async function loadAllDoubts() {
    clearAlerts();
    const container = document.getElementById("doubtsContainer");
    try {
        const response = await fetch("/api/doubts");
        if (!response.ok) {
            throw new Error("Unable to load doubts. Check that MySQL is running and spring.datasource.password in application.properties matches your MySQL password.");
        }
        const doubts = await response.json();
        container.innerHTML = "";

        if (doubts.length === 0) {
            container.innerHTML = '<div class="text-center text-muted py-3">No doubts posted yet.</div>';
            return;
        }

        doubts.forEach((doubt, index) => {
            const badgeClass = getStatusBadgeClass(doubt.status);
            const authorLabel = doubt.isAnonymous ? "Anonymously" : `by ${doubt.authorDisplay || ("Student #" + doubt.studentId)}`;
            const subjectLabel = doubt.subjectName ? `${doubt.subjectName} (${doubt.subjectCode})` : `Subject #${doubt.subjectId}`;

            // Render Official Teacher Answers
            const teacherAnswers = doubt.teacherAnswers || [];
            const teacherAnswersHtml = teacherAnswers.map(ta => `
                <div class="p-2 mb-2 rounded border border-success bg-success bg-opacity-10">
                    <div class="d-flex justify-content-between">
                        <strong class="text-success">Official Faculty Answer (${ta.teacherName || "Teacher #" + ta.teacherId})</strong>
                        <span class="badge bg-success">Verified</span>
                    </div>
                    <p class="mb-0 mt-1 small">${ta.answerText}</p>
                </div>
            `).join("");

            // Render Student Peer Answers
            const studentAnswers = doubt.answers || [];
            const studentAnswersHtml = studentAnswers.map(a => `
                <div class="p-2 mb-2 rounded border bg-white d-flex justify-content-between align-items-start">
                    <div>
                        <strong class="text-primary small">${a.studentName || ("Student #" + a.studentId)}</strong>
                        ${a.isHelpful ? '<span class="badge bg-info text-dark ms-1">Helpful</span>' : ''}
                        <p class="mb-0 mt-1 small">${a.answerText}</p>
                    </div>
                    <button class="btn btn-sm btn-outline-primary ms-2 text-nowrap" onclick="upvoteAnswer(${a.answerId})">
                        ▲ ${a.upvotes || 0}
                    </button>
                </div>
            `).join("");

            // Role-specific action controls inside each doubt
            let actionAreaHtml = "";
            if (doubt.status !== "Closed") {
                if (currentRole === "STUDENT") {
                    actionAreaHtml = `
                        <div class="mt-3 pt-2 border-top">
                            <div class="input-group input-group-sm">
                                <input type="text" id="studentAnswerInput-${doubt.doubtId}" class="form-control" placeholder="Write a peer answer (fires trg_after_student_answer)...">
                                <button class="btn btn-outline-primary" type="button" onclick="submitStudentAnswer(${doubt.doubtId})">Post Peer Answer</button>
                            </div>
                        </div>
                    `;
                } else if (currentRole === "TEACHER") {
                    actionAreaHtml = `
                        <div class="mt-3 pt-2 border-top">
                            <div class="input-group input-group-sm mb-2">
                                <input type="text" id="teacherAnswerInput-${doubt.doubtId}" class="form-control" placeholder="Write official faculty response (fires trg_after_teacher_answer)...">
                                <button class="btn btn-success" type="button" onclick="submitTeacherAnswer(${doubt.doubtId})">Post Official Answer</button>
                            </div>
                            <button class="btn btn-sm btn-outline-danger" onclick="closeDoubt(${doubt.doubtId})">Close Doubt (sp_CloseDoubt)</button>
                        </div>
                    `;
                }
            }

            if (currentRole === "ADMIN") {
                actionAreaHtml += `
                    <div class="mt-3 pt-2 border-top d-flex gap-2">
                        ${doubt.status !== "Closed" ? `<button class="btn btn-sm btn-outline-warning" onclick="closeDoubt(${doubt.doubtId})">Close Doubt (sp_CloseDoubt)</button>` : ""}
                        <button class="btn btn-sm btn-danger" onclick="deleteDoubt(${doubt.doubtId})">Delete Doubt (Admin Cascade)</button>
                    </div>
                `;
            }

            container.insertAdjacentHTML("beforeend", `
                <div class="accordion-item mb-2 shadow-sm">
                    <h2 class="accordion-header" id="heading${doubt.doubtId}">
                        <button class="accordion-button ${index !== 0 ? "collapsed" : ""}" type="button"
                                data-bs-toggle="collapse" data-bs-target="#collapse${doubt.doubtId}">
                            <div class="d-flex w-100 justify-content-between align-items-center me-3">
                                <span><strong>#${doubt.doubtId} ${doubt.title}</strong> <small class="text-muted ms-1">[${subjectLabel}]</small></span>
                                <span class="badge bg-${badgeClass}">${doubt.status}</span>
                            </div>
                        </button>
                    </h2>
                    <div id="collapse${doubt.doubtId}" class="accordion-collapse collapse ${index === 0 ? "show" : ""}">
                        <div class="accordion-body">
                            <p class="mb-2">${doubt.description || "No detailed description provided."}</p>
                            <div class="small text-muted mb-3">
                                Priority: <strong>${doubt.priority || "Medium"}</strong> | Posted ${authorLabel}
                            </div>
                            ${teacherAnswersHtml}
                            ${studentAnswersHtml}
                            ${teacherAnswers.length === 0 && studentAnswers.length === 0 ? '<div class="small text-muted fst-italic mb-2">No answers yet.</div>' : ''}
                            ${actionAreaHtml}
                        </div>
                    </div>
                </div>
            `);
        });
    } catch (error) {
        container.innerHTML = `<div class="text-center text-danger py-3">${error.message}</div>`;
        showError(error.message);
    }
}

async function submitStudentAnswer(doubtId) {
    const input = document.getElementById(`studentAnswerInput-${doubtId}`);
    const answerText = input ? input.value.trim() : "";
    if (!answerText) {
        showError("Please enter an answer before submitting.");
        return;
    }
    try {
        const response = await fetch(`/api/doubts/${doubtId}/answers`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ studentId: currentUserId, answerText })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || "Unable to post student answer.");
        showSuccess(data.message);
        await loadAllDoubts();
        await loadLeaderboard();
    } catch (error) {
        showError(error.message);
    }
}

async function submitTeacherAnswer(doubtId) {
    const input = document.getElementById(`teacherAnswerInput-${doubtId}`);
    const answerText = input ? input.value.trim() : "";
    if (!answerText) {
        showError("Please enter an official faculty answer before submitting.");
        return;
    }
    try {
        const response = await fetch(`/api/doubts/${doubtId}/teacher-answers`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ teacherId: 1, answerText })
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || "Unable to post official answer.");
        showSuccess(data.message);
        await loadAllDoubts();
    } catch (error) {
        showError(error.message);
    }
}

async function upvoteAnswer(answerId) {
    try {
        const response = await fetch(`/api/doubts/answers/${answerId}/upvote?studentId=${currentUserId}`, {
            method: "POST"
        });
        const data = await response.json();
        if (!response.ok) throw new Error(data.error || "Unable to upvote.");
        showSuccess(data.message);
        await loadAllDoubts();
        await loadLeaderboard();
    } catch (error) {
        showError(error.message);
    }
}

async function closeDoubt(doubtId) {
    try {
        const response = await fetch(`/api/doubts/${doubtId}/close`, { method: "PUT" });
        const data = await response.json();
        if (!response.ok) throw new Error("Unable to close doubt.");
        showSuccess(data.message || `Doubt #${doubtId} closed.`);
        await loadAllDoubts();
    } catch (error) {
        showError(error.message);
    }
}

async function deleteDoubt(doubtId) {
    try {
        const response = await fetch(`/api/doubts/${doubtId}`, { method: "DELETE" });
        const data = await response.json();
        if (!response.ok) throw new Error("Unable to delete doubt.");
        showSuccess(data.message || `Doubt #${doubtId} deleted.`);
        await loadAllDoubts();
    } catch (error) {
        showError(error.message);
    }
}

async function searchVectorsByTag() {
    const tag = document.getElementById("tagSearchInput").value.trim();
    const resultsDiv = document.getElementById("vectorSearchResults");
    try {
        const url = tag
            ? `/api/nosql/vectors/search?tag=${encodeURIComponent(tag)}`
            : `/api/nosql/vectors`;
        const response = await fetch(url);
        if (!response.ok) throw new Error("Unable to search MongoDB vector documents.");
        const documents = await response.json();

        if (documents.length === 0) {
            resultsDiv.innerHTML = `<div class="text-warning">No vector documents found matching "${tag}".</div>`;
            return;
        }

        resultsDiv.innerHTML = `
            <div class="fw-bold text-success mb-1">Found ${documents.length} MongoDB Vector Document(s):</div>
            ${documents.map(doc => `
                <div class="border rounded p-2 mb-1 bg-white">
                    <div class="fw-semibold text-dark">Doubt #${doc.doubtId}: ${doc.title}</div>
                    <div>Tags: ${(doc.tags || []).map(t => `<span class="badge bg-success bg-opacity-75 me-1">${t}</span>`).join("")}</div>
                    <div class="text-muted" style="font-size: 0.75rem;">Embedding: [${(doc.vectorEmbedding || []).join(", ")}]</div>
                </div>
            `).join("")}
        `;
    } catch (error) {
        showError(error.message);
    }
}

async function loadLeaderboard() {
    const container = document.getElementById("leaderboardContainer");
    try {
        const response = await fetch("/api/doubts/leaderboard");
        if (!response.ok) throw new Error("Unable to load leaderboard.");
        const rows = await response.json();
        container.innerHTML = rows.map((r, i) => `
            <div class="list-group-item d-flex justify-content-between align-items-center">
                <div>
                    <strong>#${i + 1} ${r.name}</strong> <span class="text-muted">(${r.branch})</span>
                    <div class="text-muted" style="font-size: 0.75rem;">Answers: ${r.totalAnswers} | Upvotes: ${r.totalUpvotes}</div>
                </div>
                <span class="badge bg-primary rounded-pill">${r.score} pts</span>
            </div>
        `).join("");
    } catch (error) {
        container.innerHTML = '<div class="p-3 text-danger text-center">Leaderboard unavailable</div>';
    }
}

document.getElementById("doubtForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        const response = await fetch("/api/doubts", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                studentId: currentUserId,
                subjectId: parseInt(document.getElementById("subjectId").value, 10),
                title: document.getElementById("title").value.trim(),
                description: document.getElementById("description").value.trim(),
                isAnonymous: document.getElementById("isAnonymous").checked,
                priority: document.getElementById("priority").value
            })
        });
        if (!response.ok) throw new Error("Unable to post doubt.");
        event.target.reset();
        showSuccess("Doubt submitted via Stored Procedure sp_SubmitDoubt & indexed in MongoDB!");
        await loadAllDoubts();
        await searchVectorsByTag();
    } catch (error) {
        showError(error.message);
    }
});

updateNavbarUserInfo();
loadAllDoubts();
loadLeaderboard();
searchVectorsByTag();