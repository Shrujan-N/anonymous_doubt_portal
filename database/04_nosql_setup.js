use doubt_portal_nosql;

db.createCollection("doubt_vectors");

db.doubt_vectors.createIndex({ "title": "text", "tags": "text" });

db.doubt_vectors.insertMany([
    {
        doubt_id: 1,
        title: "Difference between B-Tree and B+ Tree indexing?",
        vector_embedding: [0.123, 0.456, 0.789, 0.101],
        tags: ["SQL", "Indexing", "B-Tree"],
        created_at: new Date()
    },
    {
        doubt_id: 2,
        title: "Race condition in JK Flip Flop",
        vector_embedding: [0.999, 0.888, 0.777, 0.666],
        tags: ["Electronics", "FlipFlop"],
        created_at: new Date()
    }
]);