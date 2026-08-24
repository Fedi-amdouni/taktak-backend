package com.taktak.model;

public enum OrderStatus {
    RECEIVED,
    PREPARING,
    READY,
    PICKED_UP,
    SERVED,
    PAID,
    ARCHIVED,
    CANCELLED;

    public boolean isActive() {
        return this == RECEIVED || this == PREPARING || this == READY
                || this == PICKED_UP || this == SERVED;
    }

    public boolean isPaid() {
        return this == PAID;
    }

    public boolean isArchived() {
        return this == ARCHIVED;
    }

    public boolean isTerminal() {
        return this == ARCHIVED || this == CANCELLED;
    }
}
