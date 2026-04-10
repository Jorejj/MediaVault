package com.example.mediavault.api.streaming;

import java.io.IOException;

/**
 * Signals that a configured scraper host is dead/unreachable.
 */
public class ProviderDeadException extends IOException {
    private final String deadBaseUrl;

    public ProviderDeadException(String deadBaseUrl, String message, Throwable cause) {
        super(message, cause);
        this.deadBaseUrl = deadBaseUrl;
    }

    public String getDeadBaseUrl() {
        return deadBaseUrl;
    }
}
