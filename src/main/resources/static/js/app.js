let currentRole = "STUDENT";
let currentUserId = 1;

function showError(message) {
    const alert = document.getElementById("errorAlert");
    alert.textContent = message;
    alert.classList.remove("d-none");
}

function clearError() {
    document.getElementById("errorAlert").classList.add("d-none");
}

function switchRole(role) {
    currentRole = role;
    document.getElementById("userInfo").innerText = `Role: ${role}`;
    const alert = document.getElementById("roleAlert");
    const postCard = document.getElementById("postDoubtCard");

    if (role === "STUDENT") {
        alert.className = "alert alert-info shadow-sm";
        alert.innerHTML = "Active Mode: <strong>Student View</strong> (Ask doubts &amp; upvote peer answers)";
        postCard.style.display = "block";
    } else if (role === "TEACHER") {
        alert.className = "alert alert-warning shadow-sm";
        alert.innerHTML = "Active Mode: <strong>Teacher View</strong> (Provide authoritative answers &amp; close doubts)";
        postCard.style.display = "none";
    } else {
        alert.className = "alert alert-danger shadow-sm";
        alert.innerHTML = "Active Mode: <strong>Admin View</strong> (Moderate content &amp; manage index)";
        postCard.style.display = "none";
    }
    loadAllDoubts();
}

async function loadAllDoubts() {
    clearError();
    try {
        const response = await fetch("/api/doubts");
        if (!response.ok) throw new Error("Unable to load doubts.");
        const doubts = await response.json();
        const container = document.getElementById("doubtsContainer");
        container.innerHTML = "";
        if (doubts.length === 0) {
            container.innerHTML = '<div class="text-center text-muted">No doubts posted yet.</div>';
            return;
        }
        doubts.forEach((doubt, index) => {
            const teacherStatus = (doubt.status || "").includes("Teacher");
            const closeButton = currentRole === "TEACHER" && doubt.status !== "Closed"
                ? `<button class="btn btn-sm btn-outline-danger mb-3" onclick="closeDoubt(${doubt.doubtId})">Close Doubt</button>`
                : "";
            container.insertAdjacentHTML("beforeend", `
                <div class="accordion-item mb-2 shadow-sm">
                    <h2 class="accordion-header" id="heading${doubt.doubtId}">
                        <button class="accordion-button ${index !== 0 ? "collapsed" : ""}" type="button"
                                data-bs-toggle="collapse" data-bs-target="#collapse${doubt.doubtId}">
                            <div class="d-flex w-100 justify-content-between align-items-center me-3">
                                <span><strong>#${doubt.doubtId} ${doubt.title}</strong></span>
                                <span class="badge bg-${teacherStatus ? "success" : "primary"}">${doubt.status}</span>
                            </div>
                        </button>
                    </h2>
                    <div id="collapse${doubt.doubtId}" class="accordion-collapse collapse ${index === 0 ? "show" : ""}">
                        <div class="accordion-body">
                            <p class="text-secondary">${doubt.description || "No detailed description provided."}</p>
                            <div class="small text-muted mb-3">Priority: ${doubt.priority} |
                                Posted ${doubt.isAnonymous ? "Anonymously" : "by Student #" + doubt.studentId}</div>
                            ${closeButton}
                        </div>
                    </div>
                </div>`);
        });
    } catch (error) {
        showError(error.message);
    }
}

async function closeDoubt(doubtId) {
    try {
        const response = await fetch(`/api/doubts/${doubtId}/close`, { method: "PUT" });
        if (!response.ok) throw new Error("Unable to close doubt.");
        await loadAllDoubts();
    } catch (error) {
        showError(error.message);
    }
}

async function searchVectorsByTag() {
    const tag = document.getElementById("tagSearchInput").value.trim();
    if (!tag) return;
    try {
        const response = await fetch(`/api/nosql/vectors/search?tag=${encodeURIComponent(tag)}`);
        if (!response.ok) throw new Error("Unable to search vector documents.");
        const documents = await response.json();
        document.getElementById("vectorSearchResults").textContent =
            `Found ${documents.length} vector document(s) matching tag "${tag}".`;
    } catch (error) {
        showError(error.message);
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
                subjectId: document.getElementById("subjectId").value,
                title: document.getElementById("title").value,
                description: document.getElementById("description").value,
                isAnonymous: document.getElementById("isAnonymous").checked,
                priority: "Medium"
            })
        });
        if (!response.ok) throw new Error("Unable to post doubt.");
        event.target.reset();
        await loadAllDoubts();
    } catch (error) {
        showError(error.message);
    }
});

loadAllDoubts();
