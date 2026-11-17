// MongoDB NoSQL Vector Store Setup (mongosh script)
// Run in PowerShell / Terminal with: mongosh --file database/04_nosql_setup.js

use("doubt_portal_nosql");

// Deterministic 32-dimensional L2-normalized embedding (matches Java VectorSearchService)
function computeEmbedding(text) {
    const dims = 32;
    const vec = new Array(dims).fill(0.0);
    if (!text) return vec;

    const tokens = text.toLowerCase().replace(/[^a-z0-9+ ]/g, " ").trim().split(/\s+/).filter(Boolean);
    const features = [];
    for (const tok of tokens) {
        features.push({ feat: tok, weight: 3.0 });
        if (tok.length >= 3) {
            for (let i = 0; i <= tok.length - 3; i++) {
                features.push({ feat: tok.slice(i, i + 3), weight: 1.0 });
            }
        }
    }

    for (const { feat, weight } of features) {
        let h1 = 2166136261;
        let h2 = 5381;
        for (let i = 0; i < feat.length; i++) {
            const c = feat.charCodeAt(i);
            h1 = Math.imul(h1 ^ c, 16777619) >>> 0;
            h2 = (Math.imul(h2, 33) + c) >>> 0;
        }
        // XOR-fold high bits into low bits so bucket and sign are independent
        const m1 = (h1 ^ (h1 >>> 16)) >>> 0;
        const m2 = (h2 ^ (h2 >>> 16)) >>> 0;
        const bucket = m1 % dims;
        const sign = (m2 % 2 === 0) ? 1.0 : -1.0;
        vec[bucket] += sign * weight;
    }

    let normSq = 0;
    for (let i = 0; i < dims; i++) normSq += vec[i] * vec[i];
    const norm = Math.sqrt(normSq);
    if (norm === 0) return vec;
    return vec.map(v => Number((v / norm).toFixed(6)));
}

db.doubt_vectors.drop();
db.createCollection("doubt_vectors");

// Text index + doubtId lookup index for hybrid NoSQL queries
db.doubt_vectors.createIndex({ title: "text", tags: "text" });
db.doubt_vectors.createIndex({ doubtId: 1 });

const seedDoubts = [
    {
        doubtId: 1,
        title: "Difference between B-Tree and B+ Tree indexing?",
        description: "Why are B+ trees preferred for disk storage and range scans in relational databases?",
        tags: ["SQL", "Indexing", "B-Tree", "DBMS", "High"],
        active: true
    },
    {
        doubtId: 2,
        title: "Race condition in JK Flip Flop",
        description: "How does a master-slave JK flip-flop setup prevent the race-around condition?",
        tags: ["Electronics", "FlipFlop", "Digital", "Medium"],
        active: true
    },
    {
        doubtId: 3,
        title: "Bankers algorithm vs deadlock detection",
        description: "When should an operating system prefer deadlock avoidance over periodic cycle detection?",
        tags: ["OperatingSystems", "Deadlock", "Concurrency", "High"],
        active: true
    },
    {
        doubtId: 4,
        title: "Why is BCNF stricter than 3NF?",
        description: "Can every 3NF relation be decomposed into BCNF without losing functional dependencies?",
        tags: ["SQL", "Normalization", "DBMS", "Medium"],
        active: true
    },
    {
        doubtId: 5,
        title: "TCP slow start vs congestion avoidance",
        description: "How does congestion window cwnd grow before and after hitting ssthresh?",
        tags: ["Networks", "TCP-IP", "ExamPrep", "Low"],
        active: true
    }
];

db.doubt_vectors.insertMany(seedDoubts.map(d => ({
    doubtId: d.doubtId,
    title: d.title,
    vectorEmbedding: computeEmbedding(`${d.title} ${d.description} ${d.tags.join(" ")}`),
    tags: d.tags,
    active: d.active,
    createdAt: new Date()
})));

print("Seeded " + db.doubt_vectors.countDocuments() + " doubt_vectors documents into doubt_portal_nosql.");
printjson(db.doubt_vectors.find({}, { doubtId: 1, title: 1, tags: 1 }).toArray());
