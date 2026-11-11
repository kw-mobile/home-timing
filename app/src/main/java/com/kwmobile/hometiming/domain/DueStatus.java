package com.kwmobile.hometiming.domain;

/** Replacement due state derived only from the due date and today (docs/MVP.md, F-04). */
public enum DueStatus {
    /** Four or more days left. */
    OK,
    /** One to three days left. */
    SOON,
    /** Due today. */
    TODAY,
    /** Past the due date. */
    OVERDUE
}
