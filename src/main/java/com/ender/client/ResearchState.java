package com.ender.client;

public enum ResearchState {
    NOT_STARTED,
    IN_PROGRESS,
    // finished but not claimed yet
    COMPLETE_UNCLAIMED,
    // finished and claimed
    COMPLETE_CLAIMED
}