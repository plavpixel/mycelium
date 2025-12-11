package xyz.plavpixel.mycelium.util;

import xyz.plavpixel.mycelium.config.BotConfig;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * utility class for http requests
 */
public class HttpUtils {
    private final OkHttpClient client;
    private final BotConfig config;

    public HttpUtils() {
        this.config = BotConfig.getInstance();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getHttpTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.getHttpTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(config.getHttpTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    public String get(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MyceliumBot/2.0.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "error: http " + response.code();
            }
            return response.body() != null ? response.body().string() : "error: empty response";
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }

    public String post(String url, String jsonBody) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "MyceliumBot/2.0.0")
                .header("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "error: http " + response.code();
            }
            return response.body() != null ? response.body().string() : "error: empty response";
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }

    public String postForm(String url, FormBody formBody) {
        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "MyceliumBot/2.0.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "error: http " + response.code();
            }
            return response.body() != null ? response.body().string() : "error: empty response";
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }
}