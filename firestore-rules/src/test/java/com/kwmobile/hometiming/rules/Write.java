package com.kwmobile.hometiming.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * One write of an atomic {@code documents:commit}, the REST form of a batched write or transaction.
 * Rules see the whole commit, so {@code getAfter()}/{@code existsAfter()} checks can be tested.
 */
final class Write {
    private enum Kind { SET, CREATE, MERGE, DELETE }

    private final Kind kind;
    private final String path;
    private final Fields fields;
    private final List<String> transforms = new ArrayList<>();

    private Write(Kind kind, String path, Fields fields) {
        this.kind = kind;
        this.path = path;
        this.fields = fields;
    }

    /** Creates or replaces the document. */
    static Write set(String path, Fields fields) {
        return new Write(Kind.SET, path, fields);
    }

    /** Creates the document; the commit fails if it already exists. */
    static Write create(String path, Fields fields) {
        return new Write(Kind.CREATE, path, fields);
    }

    /** Updates only the given fields of an existing document. */
    static Write merge(String path, Fields fields) {
        return new Write(Kind.MERGE, path, fields);
    }

    static Write delete(String path) {
        return new Write(Kind.DELETE, path, null);
    }

    /** {@code FieldValue.increment(by)} applied in the same write. */
    Write increment(String field, long by) {
        requireUpdate();
        transforms.add("{\"fieldPath\":" + Json.quote(field)
                + ",\"increment\":{\"integerValue\":\"" + by + "\"}}");
        return this;
    }

    /** {@code FieldValue.serverTimestamp()} applied in the same write. */
    Write serverTime(String field) {
        requireUpdate();
        transforms.add("{\"fieldPath\":" + Json.quote(field) + ",\"setToServerValue\":\"REQUEST_TIME\"}");
        return this;
    }

    String toJson(String documentsRoot) {
        String name = Json.quote(documentsRoot + "/" + path);
        if (kind == Kind.DELETE) {
            return "{\"delete\":" + name + "}";
        }
        StringBuilder out = new StringBuilder("{\"update\":{\"name\":").append(name)
                .append(",\"fields\":").append(fields.toJson()).append('}');
        if (kind == Kind.MERGE) {
            out.append(",\"updateMask\":{\"fieldPaths\":").append(fieldNames()).append('}')
                    .append(",\"currentDocument\":{\"exists\":true}");
        } else if (kind == Kind.CREATE) {
            out.append(",\"currentDocument\":{\"exists\":false}");
        }
        if (!transforms.isEmpty()) {
            StringJoiner joined = new StringJoiner(",", "[", "]");
            transforms.forEach(joined::add);
            out.append(",\"updateTransforms\":").append(joined);
        }
        return out.append('}').toString();
    }

    private String fieldNames() {
        StringJoiner joined = new StringJoiner(",", "[", "]");
        fields.names().forEach(name -> joined.add(Json.quote(name)));
        return joined.toString();
    }

    private void requireUpdate() {
        if (kind == Kind.DELETE) {
            throw new IllegalStateException("A delete cannot carry field transforms");
        }
    }
}
