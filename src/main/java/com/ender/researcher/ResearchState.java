package com.ender.researcher;

/**
 * Shared enum for research progress states used by both server and client code.
 */
public enum ResearchState {
    NOT_STARTED,
    IN_PROGRESS,
    // finished but not claimed yet
    COMPLETE_UNCLAIMED,
    // finished and claimed
    COMPLETE_CLAIMED
}