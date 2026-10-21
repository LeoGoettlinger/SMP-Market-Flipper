package com.scmarket.automation.service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Nonblocking, cross-module lease for actions that mutate the shared Minecraft GUI. */
public final class GameplayActionGate {
    public enum Owner {
        FISHING,
        AH_DEALS,
        ORDER_TO_AH
    }

    private Owner owner;
    private long generation;

    public synchronized Optional<Lease> tryAcquire(Owner requestedOwner) {
        Objects.requireNonNull(requestedOwner, "requestedOwner");
        if (owner != null) {
            return Optional.empty();
        }
        owner = requestedOwner;
        long leaseGeneration = ++generation;
        return Optional.of(new Lease(this, requestedOwner, leaseGeneration));
    }

    public synchronized boolean availableTo(Owner requestedOwner) {
        Objects.requireNonNull(requestedOwner, "requestedOwner");
        return owner == null || owner == requestedOwner;
    }

    public synchronized Optional<Owner> currentOwner() {
        return Optional.ofNullable(owner);
    }

    private synchronized void release(Owner releasedOwner, long leaseGeneration) {
        if (owner == releasedOwner && generation == leaseGeneration) {
            owner = null;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final GameplayActionGate gate;
        private final Owner owner;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(GameplayActionGate gate, Owner owner, long generation) {
            this.gate = gate;
            this.owner = owner;
            this.generation = generation;
        }

        public Owner owner() {
            return owner;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                gate.release(owner, generation);
            }
        }
    }
}
