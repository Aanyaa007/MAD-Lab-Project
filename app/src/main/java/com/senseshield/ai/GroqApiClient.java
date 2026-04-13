package com.senseshield.ai;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GroqApiClient — lightweight HTTP client for the Groq inference API.
 * Uses the OpenAI-compatible endpoint with Llama 3.3 70B (free tier).
 *
 * Free limits (as of 2025): 14,400 req/day · 500,000 tokens/day.
 * No credit card required — sign up at console.groq.com.
 */
public class GroqApiClient {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.3-70b-versatile";
    private static final int    MAX_TOKENS   = 350;
    private static final double TEMPERATURE  = 0.7;
    private static final int    CONNECT_TIMEOUT_MS = 15_000;
    private static final int    READ_TIMEOUT_MS    = 40_000;

    public interface Callback {
        void onSuccess(String response);
        void onError(String message);
    }

    /**
     * Send a chat completion request to Groq on a background thread.
     * Result (or error) is delivered on the main thread.
     */
    public static void ask(String apiKey,
                           String systemPrompt,
                           String userMessage,
                           Callback callback) {

        new Thread(() -> {
            try {
                // ── Build JSON body ───────────────────────────────────────
                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("max_tokens", MAX_TOKENS);
                body.put("temperature", TEMPERATURE);

                JSONArray messages = new JSONArray();

                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                messages.put(sys);

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", userMessage);
                messages.put(user);

                body.put("messages", messages);

                // ── HTTP POST ────────────────────────────────────────────
                URL url = new URL(ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);

                byte[] payload = body.toString().getBytes("UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                // ── Read response ────────────────────────────────────────
                int code = conn.getResponseCode();
                java.io.InputStream is = (code == 200)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                if (code == 200) {
                    JSONObject resp = new JSONObject(sb.toString());
                    String content = resp
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();
                    post(() -> callback.onSuccess(content));
                } else {
                    post(() -> callback.onError("Groq API returned " + code
                            + ". Check your API key."));
                }

            } catch (Exception e) {
                post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }

    private static void post(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }
}
