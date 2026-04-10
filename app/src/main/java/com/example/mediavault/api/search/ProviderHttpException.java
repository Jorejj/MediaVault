package com.example.mediavault.api.search;

import java.io.IOException;

/**
 * IO exception wrapper that retains upstream provider HTTP status.
 */
public class ProviderHttpException extends IOException {
    private final String providerName;
    private final int statusCode;

    public ProviderHttpException(String providerName, int statusCode, String message) {
        super(message);
        this.providerName = providerName;
        this.statusCode = statusCode;
    }

    public String getProviderName() {
        return providerName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
