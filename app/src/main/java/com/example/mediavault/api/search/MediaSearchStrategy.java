package com.example.mediavault.api.search;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface for provider-backed media search.
 */
public interface MediaSearchStrategy {
    List<UniversalMediaResult> executeSearch(String query) throws IOException;
}
