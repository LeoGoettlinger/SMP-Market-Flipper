package com.scmarket.order;

/** Server-visible lifecycle states relevant to capacity and pickup safety. */
public enum TrackedOrderState {
    OPEN,
    PARTIALLY_FILLED,
    FILLED_UNCLAIMED,
    COLLECTED,
    CANCELLED,
    SOLD
}
