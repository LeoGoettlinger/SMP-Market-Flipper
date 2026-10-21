package com.scmarket.service;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ConfigurationSaveService {
    CompletableFuture<Void> save();
}
