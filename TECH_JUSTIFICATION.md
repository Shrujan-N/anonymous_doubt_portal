# Technology Justification

**Project:** Anonymous Classroom Doubt Portal (PES University — DBMS Level 3)

This project adopts a **polyglot persistence architecture** combining **MySQL 8.0 (RDBMS)** and **MongoDB (NoSQL Document & Vector Store)** behind a **Spring Boot 3** REST API and **Next.js** frontend. Each technology is chosen for the workload it handles best rather than forcing all data into a single engine.

---

## 1. Why MySQL 8.0 (InnoDB) for Core Portal Data?

The core workflow—students asking doubts, peers answering, voting once per answer, faculty closing doubts, and admins auditing deletions—is strongly relational and consistency-critical:

1. **Strict Referential Integrity & Cascading Rules:**
   - Normalized across **14 tables** (`student`, `teacher`, `admin`, `subject`, `tag`, `doubt`, `answer`, `teacher_answer`, `upvote`, `teacher_answer_upvote`, and 4 `*_tag` junction tables).
   - `ON DELETE CASCADE` ensures purging a doubt automatically cleans up dependent answers, upvotes, and tag links without orphaned rows.
   - `ON DELETE SET NULL` on `subject.teacher_id` and `deleted_by_admin_id` preserves historical doubts/subjects even if an account is retired.
2. **Constraint & State-Machine Enforcement in the Database:**
   - Composite primary keys `PRIMARY KEY (student_id, answer_id)` in `upvote` and `PRIMARY KEY (student_id, teacher_answer_id)` in `teacher_answer_upvote` prevent duplicate voting at the storage engine level—even under concurrent requests.
   - `ENUM('Pending', 'Answered by Student', 'Answered by Teacher', 'Closed')` and `CHECK (semester BETWEEN 1 AND 8)` guarantee valid domain values.
   - Database triggers (`trg_after_student_answer`, `trg_after_teacher_answer`, `trg_prevent_answer_on_closed_doubt`, `trg_after_upvote_insert`) enforce lifecycle state transitions directly inside MySQL regardless of which client inserts a row.
3. **Analytical Queries & Full-Text Search:**
   - Supports multi-table JOINs, correlated subqueries, window functions (`RANK()`, `DENSE_RANK()`), table-based recursive CTEs (`WITH RECURSIVE` over `doubt_tag`), and `FULLTEXT INDEX` (`MATCH ... AGAINST`) on `doubt(title, description)`.
4. **Native Database-Level RBAC:**
   - MySQL 8.0 roles (`CREATE ROLE`, `GRANT`, `REVOKE`, `SET DEFAULT ROLE`) allow enforcing table- and column-level permissions (`role_portal_student`, `role_portal_teacher`, `role_portal_admin`) directly from the MySQL CLI.

---

## 2. Why MongoDB (NoSQL) for Vector Embeddings & Semantic Topic Indexing?

While MySQL stores structured academic records, semantic question discovery benefits from a document store:

1. **Native Storage for Numerical Embedding Arrays & Dynamic Tags:**
   - Each doubt's **32-dimensional L2-normalized embedding** (`vectorEmbedding: [0.214, ...]`) and variable-length tag array (`tags: ["SQL", "Indexing", "B-Tree"]`) map directly to a self-contained JSON/BSON document in `doubt_vectors`.
   - Storing embeddings in MySQL would either require brittle normalized child rows (`(vector_id, dim_index, val)`) or opaque BLOBs that cannot be inspected cleanly in CLI.
2. **Flexible Schema Evolution & Full RUD:**
   - Faculty/Admins can re-tag or re-index topics (`PUT /api/nosql/vectors/{id}/tags`, `POST /api/nosql/vectors`, `DELETE /api/nosql/vectors/{id}`) and compute cosine similarity across vector documents without altering relational tables.
3. **Dual-Engine Complementarity:**
   - MySQL handles **exact keyword/phrase matching** via `FULLTEXT INDEX` (`MATCH ... AGAINST`).
   - MongoDB handles **vector-based semantic ranking** (cosine similarity over weighted word + character-shingle embeddings) and text-indexed tag filtering.
   - When an Admin soft-deletes or hard-purges a doubt in MySQL, `DoubtController` propagates the delete to MongoDB (`deleteByDoubtId`) to prevent stale search hits.

---

## 3. Why Spring Boot 3 (Java 17) + Next.js?

- **Spring Boot (`JdbcTemplate` + Spring Data JPA + Spring Data MongoDB):**
  - Lets us mix JPA repositories (`StudentRepository`, `DoubtRepository`, `DoubtVectorMongoRepository`) with direct `JdbcTemplate` SQL calls so stored procedures (`CALL sp_SubmitDoubt`), stored functions (`fn_CalculateStudentReputation`), recursive CTEs, and `MATCH ... AGAINST` run natively in MySQL without ORM workarounds.
  - Supports session-based + header-token RBAC checks (`AuthService`) that match our MySQL privilege tiers.
- **Next.js 15 (App Router) + Tailwind CSS:**
  - Provides a responsive, componentized multi-role UI for Students, Faculty, and Admins with `/api/*` rewrite proxying to Spring Boot (`localhost:8080`) so cookies/sessions stay same-origin cleanly during development and viva demos.
