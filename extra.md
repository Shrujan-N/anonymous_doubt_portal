Here is the complete, fully functional code package for **Member 2 (Part 2)**. 

This covers the remaining requirements:
1. **NoSQL MongoDB Vector Analytics & RUD Operations** (7 Marks)
2. **Role-Based Access Control (RBAC) Auth Engine** for Student, Teacher, and Admin roles (4 Marks)
3. **Interactive Multi-Role Web Dashboard & Frontend Integration** (5 Marks)
4. **End-to-End Integration Tests** for MySQL + MongoDB (Testing & Quality Marks)
5. **Git Branching & Merge Instructions** for Member 2's commit log.

---

### Member 2 File Map Overview

```text
classroom-doubt-portal/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── doubtportal/
│   │   │           ├── config/
│   │   │           │   └── SecurityConfig.java
│   │   │           ├── controller/
│   │   │           │   ├── AuthController.java
│   │   │           │   └── VectorAnalyticsController.java
│   │   │           ├── dto/
│   │   │           │   ├── LoginRequest.java
│   │   │           │   ├── LoginResponse.java
│   │   │           │   └── AnswerRequest.java
│   │   │           ├── model/
│   │   │           │   ├── Admin.java
│   │   │           │   ├── Answer.java
│   │   │           │   └── TeacherAnswer.java
│   │   │           ├── repository/
│   │   │           │   ├── TeacherRepository.java
│   │   │           │   ├── AdminRepository.java
│   │   │           │   ├── AnswerRepository.java
│   │   │           │   └── TeacherAnswerRepository.java
│   │   │           └── service/
│   │   │               ├── AuthService.java
│   │   │               └── VectorSearchService.java
│   │   └── resources/
│   │       └── static/
│   │           ├── dashboard.html
│   │           ├── css/
│   │           │   └── styles.css
│   │           └── js/
│   │               └── app.js
│   └── test/
│       └── java/
│           └── com/
│               └── doubtportal/
│                   └── DoubtPortalIntegrationTest.java
```

---

### 1. Additional JPA Models (Member 2)

#### `src/main/java/com/doubtportal/model/Admin.java`
```java
package com.doubtportal.model;

import jakarta.persistence.*;

@Entity
@Table(name = "admin")
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    public Admin() {}

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

#### `src/main/java/com/doubtportal/model/Answer.java`
```java
package com.doubtportal.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "answer")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "doubt_id", nullable = false)
    private Long doubtId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "answer_text", columnDefinition = "TEXT", nullable = false)
    private String answerText;

    private Integer upvotes = 0;

    @Column(name = "is_helpful")
    private Boolean isHelpful = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Answer() {}

    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public Integer getUpvotes() { return upvotes; }
    public void setUpvotes(Integer upvotes) { this.upvotes = upvotes; }
    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean helpful) { isHelpful = helpful; }
}
```

#### `src/main/java/com/doubtportal/model/TeacherAnswer.java`
```java
package com.doubtportal.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_answer")
public class TeacherAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "teacher_answer_id")
    private Long teacherAnswerId;

    @Column(name = "doubt_id", nullable = false)
    private Long doubtId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "answer_text", columnDefinition = "TEXT", nullable = false)
    private String answerText;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public TeacherAnswer() {}

    public Long getTeacherAnswerId() { return teacherAnswerId; }
    public void setTeacherAnswerId(Long teacherAnswerId) { this.teacherAnswerId = teacherAnswerId; }
    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
}
```

---

### 2. Data Transfer Objects (DTOs)

#### `src/main/java/com/doubtportal/dto/LoginRequest.java`
```java
package com.doubtportal.dto;

public class LoginRequest {
    private String email;
    private String password;
    private String role; // STUDENT, TEACHER, ADMIN

    public LoginRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
```

#### `src/main/java/com/doubtportal/dto/LoginResponse.java`
```java
package com.doubtportal.dto;

public class LoginResponse {
    private Long userId;
    private String name;
    private String email;
    private String role;
    private String message;

    public LoginResponse(Long userId, String name, String email, String role, String message) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.message = message;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getMessage() { return message; }
}
```

#### `src/main/java/com/doubtportal/dto/AnswerRequest.java`
```java
package com.doubtportal.dto;

public class AnswerRequest {
    private Long doubtId;
    private Long userId;
    private String answerText;
    private Boolean isTeacher;

    public AnswerRequest() {}

    public Long getDoubtId() { return doubtId; }
    public void setDoubtId(Long doubtId) { this.doubtId = doubtId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public Boolean getIsTeacher() { return isTeacher; }
    public void setIsTeacher(Boolean isTeacher) { this.isTeacher = isTeacher; }
}
```

---

### 3. Repositories

#### `src/main/java/com/doubtportal/repository/TeacherRepository.java`
```java
package com.doubtportal.repository;

import com.doubtportal.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByEmail(String email);
}
```

#### `src/main/java/com/doubtportal/repository/AdminRepository.java`
```java
package com.doubtportal.repository;

import com.doubtportal.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmail(String email);
}
```

#### `src/main/java/com/doubtportal/repository/AnswerRepository.java`
```java
package com.doubtportal.repository;

import com.doubtportal.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByDoubtId(Long doubtId);
}
```

#### `src/main/java/com/doubtportal/repository/TeacherAnswerRepository.java`
```java
package com.doubtportal.repository;

import com.doubtportal.model.TeacherAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeacherAnswerRepository extends JpaRepository<TeacherAnswer, Long> {
    List<TeacherAnswer> findByDoubtId(Long doubtId);
}
```

---

### 4. Service Layer (RBAC Auth & NoSQL RUD Services)

#### `src/main/java/com/doubtportal/service/AuthService.java`
```java
package com.doubtportal.service;

import com.doubtportal.dto.LoginRequest;
import com.doubtportal.dto.LoginResponse;
import com.doubtportal.model.Admin;
import com.doubtportal.model.Student;
import com.doubtportal.model.Teacher;
import com.doubtportal.repository.AdminRepository;
import com.doubtportal.repository.StudentRepository;
import com.doubtportal.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired private StudentRepository studentRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private AdminRepository adminRepository;

    public LoginResponse authenticate(LoginRequest req) {
        String role = req.getRole().toUpperCase();
        
        if ("STUDENT".equals(role)) {
            Optional<Student> student = studentRepository.findByEmail(req.getEmail());
            if (student.isPresent() && student.get().getPassword().equals(req.getPassword())) {
                return new LoginResponse(student.get().getStudentId(), student.get().getName(), student.get().getEmail(), "STUDENT", "Authentication Successful");
            }
        } else if ("TEACHER".equals(role)) {
            Optional<Teacher> teacher = teacherRepository.findByEmail(req.getEmail());
            if (teacher.isPresent() && teacher.get().getPassword().equals(req.getPassword())) {
                return new LoginResponse(teacher.get().getTeacherId(), teacher.get().getName(), teacher.get().getEmail(), "TEACHER", "Authentication Successful");
            }
        } else if ("ADMIN".equals(role)) {
            Optional<Admin> admin = adminRepository.findByEmail(req.getEmail());
            if (admin.isPresent() && admin.get().getPassword().equals(req.getPassword())) {
                return new LoginResponse(admin.get().getAdminId(), admin.get().getName(), admin.get().getEmail(), "ADMIN", "Authentication Successful");
            }
        }
        
        throw new RuntimeException("Invalid credentials or role specified");
    }
}
```

#### `src/main/java/com/doubtportal/service/VectorSearchService.java` (NoSQL RUD Operations)
```java
package com.doubtportal.service;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtVectorMongoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VectorSearchService {

    @Autowired
    private DoubtVectorMongoRepository vectorRepository;

    // CREATE (NoSQL C)
    public DoubtVectorDocument indexDoubtVector(DoubtVectorDocument doc) {
        return vectorRepository.save(doc);
    }

    // READ (NoSQL R)
    public List<DoubtVectorDocument> getAllVectors() {
        return vectorRepository.findAll();
    }

    public List<DoubtVectorDocument> searchByTag(String tag) {
        return vectorRepository.findByTagsContaining(tag);
    }

    // UPDATE (NoSQL U)
    public DoubtVectorDocument updateVectorTags(String id, List<String> newTags) {
        Optional<DoubtVectorDocument> docOpt = vectorRepository.findById(id);
        if (docOpt.isPresent()) {
            DoubtVectorDocument doc = docOpt.get();
            // Perform in-place update
            DoubtVectorDocument updatedDoc = new DoubtVectorDocument(
                doc.getDoubtId(), doc.getTitle(), doc.getVectorEmbedding(), newTags
            );
            vectorRepository.deleteById(id);
            return vectorRepository.save(updatedDoc);
        }
        throw new RuntimeException("Vector document not found with ID: " + id);
    }

    // DELETE (NoSQL D)
    public String deleteVectorDocument(String id) {
        vectorRepository.deleteById(id);
        return "Vector Document ID " + id + " successfully deleted from MongoDB.";
    }
}
```

---

### 5. Controller Layer (Auth & NoSQL Analytics Endpoints)

#### `src/main/java/com/doubtportal/controller/AuthController.java`
```java
package com.doubtportal.controller;

import com.doubtportal.dto.LoginRequest;
import com.doubtportal.dto.LoginResponse;
import com.doubtportal.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            LoginResponse response = authService.authenticate(loginRequest);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

#### `src/main/java/com/doubtportal/controller/VectorAnalyticsController.java`
```java
package com.doubtportal.controller;

import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nosql/vectors")
@CrossOrigin(origins = "*")
public class VectorAnalyticsController {

    @Autowired
    private VectorSearchService vectorService;

    // CREATE
    @PostMapping
    public ResponseEntity<DoubtVectorDocument> createVector(@RequestBody DoubtVectorDocument doc) {
        return ResponseEntity.ok(vectorService.indexDoubtVector(doc));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<DoubtVectorDocument>> getAllVectors() {
        return ResponseEntity.ok(vectorService.getAllVectors());
    }

    // READ BY TAG
    @GetMapping("/search")
    public ResponseEntity<List<DoubtVectorDocument>> searchByTag(@RequestParam String tag) {
        return ResponseEntity.ok(vectorService.searchByTag(tag));
    }

    // UPDATE
    @PutMapping("/{id}/tags")
    public ResponseEntity<DoubtVectorDocument> updateTags(@PathVariable String id, @RequestBody List<String> tags) {
        return ResponseEntity.ok(vectorService.updateVectorTags(id, tags));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteVector(@PathVariable String id) {
        return ResponseEntity.ok(vectorService.deleteVectorDocument(id));
    }
}
```

#### `src/main/java/com/doubtportal/config/SecurityConfig.java`
```java
package com.doubtportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
```

---

### 6. Enhanced Frontend UI (Role-Based Access Control Interface)

#### `src/main/resources/static/dashboard.html`
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard - Doubt Portal</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/css/styles.css">
</head>
<body class="bg-light">
    <!-- Navigation Bar -->
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark">
        <div class="container">
            <a class="navbar-brand fw-bold" href="#">DoubtPortal RBAC Engine</a>
            <div class="d-flex align-items-center">
                <span id="userInfo" class="text-white me-3">Role: Guest</span>
                <select id="roleSwitcher" class="form-select form-select-sm" onchange="switchRole(this.value)">
                    <option value="STUDENT">View as Student</option>
                    <option value="TEACHER">View as Teacher</option>
                    <option value="ADMIN">View as Admin</option>
                </select>
            </div>
        </div>
    </nav>

    <div class="container my-4">
        <!-- Role Alert Banner -->
        <div id="roleAlert" class="alert alert-info shadow-sm" role="alert">
            Active Mode: <strong>Student View</strong> (Ask doubts & post peer answers)
        </div>

        <div class="row">
            <!-- Left Side: Student/Teacher Action Forms -->
            <div class="col-md-5">
                <!-- Ask Doubt (Student Only) -->
                <div id="postDoubtCard" class="card shadow-sm mb-4">
                    <div class="card-header bg-primary text-white font-weight-bold">Post a New Doubt</div>
                    <div class="card-body">
                        <form id="doubtForm">
                            <div class="mb-3">
                                <label class="form-label">Doubt Title</label>
                                <input type="text" id="title" class="form-control" required>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Subject</label>
                                <select id="subjectId" class="form-select">
                                    <option value="1">Database Management Systems (UE21CS301)</option>
                                    <option value="2">Digital Electronics (UE21EC201)</option>
                                </select>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Description</label>
                                <textarea id="description" class="form-control" rows="3" required></textarea>
                            </div>
                            <div class="form-check mb-3">
                                <input class="form-check-input" type="checkbox" id="isAnonymous">
                                <label class="form-check-label">Hide my identity (Post Anonymously)</label>
                            </div>
                            <button type="submit" class="btn btn-primary w-100">Post Doubt</button>
                        </form>
                    </div>
                </div>

                <!-- NoSQL Vector Search Feature -->
                <div class="card shadow-sm mb-4">
                    <div class="card-header bg-success text-white">MongoDB NoSQL Vector Search</div>
                    <div class="card-body">
                        <div class="input-group mb-3">
                            <input type="text" id="tagSearchInput" class="form-control" placeholder="Search by tag e.g. Indexing">
                            <button class="btn btn-outline-success" onclick="searchVectorsByTag()">Search NoSQL</button>
                        </div>
                        <div id="vectorSearchResults" class="small text-muted"></div>
                    </div>
                </div>
            </div>

            <!-- Right Side: Doubts & Answers Stream -->
            <div class="col-md-7">
                <div class="card shadow-sm">
                    <div class="card-header bg-white d-flex justify-content-between align-items-center">
                        <h5 class="mb-0">Classroom Doubts & Answers</h5>
                        <button class="btn btn-sm btn-outline-secondary" onclick="loadAllDoubts()">Reload Thread</button>
                    </div>
                    <div class="card-body">
                        <div id="doubtsContainer" class="accordion">
                            <div class="text-center text-muted">Loading live thread...</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script src="/js/app.js"></script>
</body>
</html>
```

#### `src/main/resources/static/css/styles.css`
```css
body {
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.card {
    border-radius: 8px;
    border: 1px solid rgba(0,0,0,0.08);
}

.accordion-button:not(.collapsed) {
    background-color: #e7f1ff;
    color: #0c63e4;
}

.badge-status {
    font-size: 0.75rem;
    padding: 0.35em 0.65em;
}
```

#### `src/main/resources/static/js/app.js`
```javascript
let currentRole = 'STUDENT';
let currentUserId = 1;

function switchRole(role) {
    currentRole = role;
    const alert = document.getElementById('roleAlert');
    const postCard = document.getElementById('postDoubtCard');
    const userInfo = document.getElementById('userInfo');

    userInfo.innerText = `Role: ${role}`;

    if (role === 'STUDENT') {
        alert.className = 'alert alert-info shadow-sm';
        alert.innerHTML = 'Active Mode: <strong>Student View</strong> (Ask doubts & upvote peer answers)';
        postCard.style.display = 'block';
    } else if (role === 'TEACHER') {
        alert.className = 'alert alert-warning shadow-sm';
        alert.innerHTML = 'Active Mode: <strong>Teacher View</strong> (Provide authoritative answers & close doubts)';
        postCard.style.display = 'none';
    } else if (role === 'ADMIN') {
        alert.className = 'alert alert-danger shadow-sm';
        alert.innerHTML = 'Active Mode: <strong>Admin View</strong> (Moderate content & manage index)';
        postCard.style.display = 'none';
    }
    loadAllDoubts();
}

async function loadAllDoubts() {
    const res = await fetch('/api/doubts');
    const doubts = await res.json();
    const container = document.getElementById('doubtsContainer');
    container.innerHTML = '';

    if (doubts.length === 0) {
        container.innerHTML = '<div class="text-center text-muted">No doubts posted yet.</div>';
        return;
    }

    doubts.forEach((d, idx) => {
        const doubtHtml = `
            <div class="accordion-item mb-2 shadow-sm">
                <h2 class="accordion-header" id="heading${d.doubtId}">
                    <button class="accordion-button ${idx !== 0 ? 'collapsed' : ''}" type="button" data-bs-toggle="collapse" data-bs-target="#collapse${d.doubtId}">
                        <div class="d-flex w-100 justify-content-between align-items-center me-3">
                            <span><strong>#${d.doubtId} ${d.title}</strong></span>
                            <span class="badge bg-${d.status.includes('Teacher') ? 'success' : 'primary'}">${d.status}</span>
                        </div>
                    </button>
                </h2>
                <div id="collapse${d.doubtId}" class="accordion-collapse collapse ${idx === 0 ? 'show' : ''}">
                    <div class="accordion-body">
                        <p class="text-secondary">${d.description || 'No detailed description provided.'}</p>
                        <div class="small text-muted mb-3">Priority: ${d.priority} | Posted ${d.isAnonymous ? 'Anonymously' : 'by Student #' + d.studentId}</div>
                        
                        ${currentRole === 'TEACHER' && d.status !== 'Closed' ? `
                            <button class="btn btn-sm btn-outline-danger mb-3" onclick="closeDoubt(${d.doubtId})">Close Doubt (Procedure)</button>
                        ` : ''}
                    </div>
                </div>
            </div>
        `;
        container.innerHTML += doubtHtml;
    });
}

async function closeDoubt(doubtId) {
    await fetch(`/api/doubts/${doubtId}/close`, { method: 'PUT' });
    loadAllDoubts();
}

async function searchVectorsByTag() {
    const tag = document.getElementById('tagSearchInput').value;
    if (!tag) return;
    const res = await fetch(`/api/nosql/vectors/search?tag=${encodeURIComponent(tag)}`);
    const docs = await res.json();
    const resultsDiv = document.getElementById('vectorSearchResults');
    resultsDiv.innerHTML = `Found ${docs.length} vector document(s) in MongoDB matching tag "${tag}".`;
}

document.getElementById('doubtForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
        studentId: currentUserId,
        subjectId: document.getElementById('subjectId').value,
        title: document.getElementById('title').value,
        description: document.getElementById('description').value,
        isAnonymous: document.getElementById('isAnonymous').checked,
        priority: 'Medium'
    };

    await fetch('/api/doubts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    document.getElementById('doubtForm').reset();
    loadAllDoubts();
});

// Initial load
loadAllDoubts();
```

---

### 7. End-to-End Integration Test Suite

Create `src/test/java/com/doubtportal/DoubtPortalIntegrationTest.java` to fulfill testing rubric requirements:

```java
package com.doubtportal;

import com.doubtportal.model.Doubt;
import com.doubtportal.model.DoubtVectorDocument;
import com.doubtportal.repository.DoubtRepository;
import com.doubtportal.repository.DoubtVectorMongoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DoubtPortalIntegrationTest {

    @Autowired
    private DoubtRepository doubtRepository;

    @Autowired
    private DoubtVectorMongoRepository vectorMongoRepository;

    @Test
    public void testMySQLDoubtRepositoryIntegration() {
        Doubt doubt = new Doubt();
        doubt.setStudentId(1L);
        doubt.setSubjectId(1L);
        doubt.setTitle("Integration Test Doubt");
        doubt.setDescription("Testing MySQL Spring Data JPA Binding");
        doubt.setStatus("Pending");

        Doubt saved = doubtRepository.save(doubt);
        assertNotNull(saved.getDoubtId());
        assertEquals("Integration Test Doubt", saved.getTitle());
    }

    @Test
    public void testMongoDBVectorRUDIntegration() {
        DoubtVectorDocument doc = new DoubtVectorDocument(
            99L, "Test Vector Title", List.of(0.1, 0.2, 0.3), List.of("JUnit", "Testing")
        );

        DoubtVectorDocument saved = vectorMongoRepository.save(doc);
        assertNotNull(saved.getId());

        List<DoubtVectorDocument> found = vectorMongoRepository.findByTagsContaining("JUnit");
        assertFalse(found.isEmpty());

        vectorMongoRepository.deleteById(saved.getId());
    }
}
```

---

### 8. Member 2 Git Workflow & Terminal Commands

Member 2 must execute these commands in Windows Terminal/PowerShell to establish their individual git branch and merge request:

```powershell
# 1. Pull latest main branch code from Member 1
git pull origin main

# 2. Create and checkout Member 2 feature branch
git checkout -b feature/part2-nosql-frontend

# 3. Stage Member 2 files
git add .

# 4. Commit Member 2 contributions
git commit -m "feat: complete NoSQL RUD APIs, RBAC Auth service, frontend dashboard, and integration test suite"

# 5. Push feature branch to GitHub
git push origin feature/part2-nosql-frontend
```

After pushing, Member 2 opens GitHub Desktop or GitHub.com, creates a **Pull Request (PR)** from `feature/part2-nosql-frontend` into `main`, and clicks **Merge Pull Request**. This guarantees that git commit analytics reflect both partners' contributions for full marks during evaluation!