package com.example.mediavault.api.streaming;

import com.example.mediavault.api.consumet.ConsumetStreamResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface DynamicScraperApiService {
    @GET
    Call<ConsumetStreamResponse> getStreamingLinks(@Url String relativePath);
}
