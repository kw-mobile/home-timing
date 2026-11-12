package com.kwmobile.hometiming.data.firestore;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

/** Preserves the server-verified record state when a domain model replaces a document. */
final class RecordState {
    private RecordState() {
    }

    static long count(DocumentSnapshot snapshot) {
        Long count = snapshot.getLong(Schema.Records.COUNT);
        if (count == null || count < 0 || !(snapshot.get(Schema.Records.DELETING) instanceof Boolean)) {
            // Legacy documents need a privileged backfill; guessing zero would lose their records.
            throw new ItemException.Rejection(ItemError.UNKNOWN);
        }
        return count;
    }

    static void requireActive(DocumentSnapshot snapshot) {
        count(snapshot);
        if (Boolean.TRUE.equals(snapshot.getBoolean(Schema.Records.DELETING))) {
            throw new ItemException.Rejection(ItemError.NOT_ALLOWED);
        }
    }

    static Map<String, Object> preserve(Map<String, Object> fields, DocumentSnapshot snapshot, String newRecordId) {
        requireActive(snapshot);
        fields.put(Schema.Records.COUNT, count(snapshot) + (newRecordId == null ? 0 : 1));
        String mutation = newRecordId == null ? snapshot.getString(Schema.Records.MUTATION_ID) : newRecordId;
        if (mutation != null) {
            fields.put(Schema.Records.MUTATION_ID, mutation);
        }
        return fields;
    }
}
