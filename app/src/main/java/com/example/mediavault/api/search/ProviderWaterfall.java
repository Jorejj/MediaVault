package com.example.mediavault.api.search;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chain-of-responsibility fallback runner for provider search strategies.
 */
public class ProviderWaterfall {
    private static final String TAG = "ProviderWaterfall";

    /**
     * Runs search through each strategy in order until one succeeds.
     * Falls through on IO failures and retryable HTTP status (429/5xx).
     */
    public List<UniversalMediaResult> searchWithFallback(List<MediaSearchStrategy> strategies, String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        if (strategies == null || strategies.isEmpty()) {
            throw new IOException("No providers configured for search waterfall.");
        }

        List<String> attemptedProviders = new ArrayList<>();
        IOException lastRetryableError = null;

        for (MediaSearchStrategy strategy : strategies) {
            if (strategy == null) {
                continue;
            }
            String providerName = strategy.getClass().getSimpleName();
            attemptedProviders.add(providerName);

            try {
                List<UniversalMediaResult> results = strategy.executeSearch(query);
                if (results != null && !results.isEmpty()) {
                    Log.d(TAG, "Search success via " + providerName + " with " + results.size() + " results");
                    return results;
                }
                Log.w(TAG, "Provider returned no results, trying next: " + providerName);
            } catch (ProviderHttpException httpEx) {
                int code = httpEx.getStatusCode();
                if (code == 429 || code >= 500) {
                    lastRetryableError = httpEx;
                    Log.w(TAG, "Retryable HTTP " + code + " from " + providerName + ", trying next provider");
                    continue;
                }
                throw httpEx;
            } catch (IOException ioEx) {
                lastRetryableError = ioEx;
                Log.w(TAG, "IO failure from " + providerName + ", trying next provider: " + ioEx.getMessage());
            }
        }

        if (lastRetryableError != null) {
            throw new IOException("All providers failed: " + attemptedProviders, lastRetryableError);
        }
        throw new IOException("All providers returned empty results: " + attemptedProviders);
    }
}
