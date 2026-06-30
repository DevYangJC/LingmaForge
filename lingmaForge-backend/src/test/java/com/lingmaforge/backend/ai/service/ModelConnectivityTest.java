package com.lingmaforge.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lingmaforge.backend.infra.config.LingmaModelsProperties;
import com.lingmaforge.backend.infra.config.LingmaModelsProperties.ModelConfig;

import dev.langchain4j.model.chat.ChatModel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * All LLM model connectivity smoke test.
 *
 * <p>Iterates every model configured in {@code lingma.models} that has a valid API key,
 * sends a single minimal chat request, and verifies the response is non-empty.</p>
 *
 * <p>Models without API keys are automatically skipped (not failures).</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 *   ./mvnw test -Dtest="ModelConnectivityTest"
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("LLM Model Connectivity Test")
class ModelConnectivityTest {

    private static final Logger log = LoggerFactory.getLogger(ModelConnectivityTest.class);

    private static final String PING_PROMPT =
            "Reply with exactly this JSON and nothing else: {\"status\":\"ok\",\"word\":\"pong\"}";

    @Autowired
    private Map<String, ChatModel> chatModels;

    @Autowired
    private LingmaModelsProperties modelsProperties;

    private static final List<ModelResult> results = new ArrayList<>();

    @BeforeAll
    static void printHeader() {
        log.info("");
        log.info("===============================================================");
        log.info("  LingmaForge - LLM Model Connectivity Test");
        log.info("  Each model receives 1 minimal chat request");
        log.info("  Expected: {\"status\":\"ok\",\"word\":\"pong\"}");
        log.info("  Cost: ~10-50 input + ~10 output tokens per model");
        log.info("===============================================================");
        log.info("");
    }

    @TestFactory
    @DisplayName("Per-model connectivity")
    List<DynamicTest> testAllModels() {
        Map<String, ModelConfig> allConfigured = modelsProperties.models();
        if (allConfigured == null || allConfigured.isEmpty()) {
            return List.of(DynamicTest.dynamicTest("no models configured", () -> {
                log.warn("No models in lingma.models! Please configure at least one model.");
            }));
        }

        List<DynamicTest> tests = new ArrayList<>();
        for (String modelName : allConfigured.keySet()) {
            ModelConfig cfg = allConfigured.get(modelName);
            ChatModel model = chatModels.get(modelName);

            tests.add(DynamicTest.dynamicTest(modelName, () -> {
                if (model == null) {
                    String reason = apiKeyMissing(cfg)
                            ? "API Key not set (env var missing or empty)"
                            : "Filtered by LangChain4jConfig";
                    log.info("[SKIP] {} - {}", modelName, reason);
                    results.add(ModelResult.skipped(modelName, cfg.modelName(), cfg.provider(), reason));
                    return;
                }

                log.info("-----------------------------------------------------------");
                log.info("[TEST] {} | provider={} | model={} | baseUrl={}",
                        modelName, cfg.provider(), cfg.modelName(), cfg.baseUrl());

                Instant start = Instant.now();
                try {
                    String text = model.chat(PING_PROMPT);
                    Duration elapsed = Duration.between(start, Instant.now());

                    log.info("[RESP] {}ms | chars={}",
                            elapsed.toMillis(), text.length());
                    log.info("[BODY] {}",
                            text.length() > 300 ? text.substring(0, 300) + "..." : text);

                    assertThat(text)
                            .describedAs("[%s] response must not be blank", modelName)
                            .isNotBlank();

                    results.add(ModelResult.passed(
                            modelName, cfg.modelName(), cfg.provider(),
                            elapsed.toMillis(), text.length()));
                    log.info("[PASS] {} ({})ms", modelName, elapsed.toMillis());

                } catch (Exception e) {
                    Duration elapsed = Duration.between(start, Instant.now());
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    if (errMsg.length() > 150) errMsg = errMsg.substring(0, 150) + "...";

                    log.error("[FAIL] {} ({}ms) - {}", modelName, elapsed.toMillis(), errMsg);
                    results.add(ModelResult.failed(
                            modelName, cfg.modelName(), cfg.provider(),
                            elapsed.toMillis(), errMsg));
                    throw new AssertionError(
                            "Model [" + modelName + "] connectivity failed: " + errMsg, e);
                }
            }));
        }

        // Summary test runs last
        tests.add(DynamicTest.dynamicTest("=== SUMMARY ===", this::printSummary));
        return tests;
    }

    private void printSummary() {
        log.info("");
        log.info("========================== SUMMARY ==========================");
        log.info(String.format("%-24s %-18s %-5s %7s %7s  %s",
                "ALIAS", "MODEL", "STATUS", "LATENCY", "CHARS", "NOTE"));
        log.info("-----------------------------------------------------------");

        for (ModelResult r : results) {
            log.info(String.format("%-24s %-18s %-5s %6sms %6s  %s",
                    trunc(r.alias, 24),
                    trunc(r.actualModel, 18),
                    r.status,
                    r.latencyMs,
                    r.chars >= 0 ? String.valueOf(r.chars) : "-",
                    trunc(r.note, 40)));
        }

        long passed = results.stream().filter(r -> "PASS".equals(r.status)).count();
        long failed = results.stream().filter(r -> "FAIL".equals(r.status)).count();
        long skipped = results.stream().filter(r -> "SKIP".equals(r.status)).count();

        log.info("-----------------------------------------------------------");
        log.info("TOTAL: {} passed / {} failed / {} skipped", passed, failed, skipped);

        if (failed > 0) {
            log.error("{} model(s) failed! Check API keys and network.", failed);
        }
        if (passed == 0 && skipped > 0) {
            log.warn("All models skipped. Set at least one API key env var!");
            log.warn("Example: set DEEPSEEK_API_KEY=sk-xxxx");
        }

        assertThat(passed)
                .describedAs("At least 1 model must pass (got 0 pass / %d skip / %d fail)", skipped, failed)
                .isGreaterThan(0);
    }

    private boolean apiKeyMissing(ModelConfig cfg) {
        String key = cfg.apiKey();
        return key == null || key.isBlank() || key.startsWith("${");
    }

    private static String trunc(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    private record ModelResult(
            String alias,
            String actualModel,
            String provider,
            String status,
            long latencyMs,
            int chars,
            String note) {

        static ModelResult passed(String alias, String model, String provider,
                long ms, int chars) {
            return new ModelResult(alias, model, provider, "PASS", ms, chars, "");
        }

        static ModelResult failed(String alias, String model, String provider,
                long ms, String error) {
            return new ModelResult(alias, model, provider, "FAIL", ms, -1, error);
        }

        static ModelResult skipped(String alias, String model, String provider, String reason) {
            return new ModelResult(alias, model, provider, "SKIP", 0, -1, reason);
        }
    }
}
