package com.kwmobile.hometiming.data.firestore;

/** A rejected category change; never exposes a document or account value. */
public final class CategoryException extends Exception {
    public final String code;
    public CategoryException(String code) { super(code); this.code = code; }
    public CategoryException(String code, Throwable cause) { super(code, cause); this.code = code; }
}
