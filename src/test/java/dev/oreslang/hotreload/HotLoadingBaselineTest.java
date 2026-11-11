package dev.oreslang.hotreload;

import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the *real* Oreslang HotReloadManager, never a mock
 * compiler or fake actor runtime. OTP supervision is tracked separately.
 */
@Timeout(40)
final class HotLoadingBaselineTest {
    private static final ExecutionProfile JIT = ExecutionProfile.serverJit();
    private static final String SAFE_PROGRAM =
            "pub routine main(): void { return; }";

    @Test
    void requiresHotCodeLoadAuthorityFromSupervisor() {
        assertThrows(SecurityException.class,
                () -> new HotReloadManager(IsolatePolicy.strictFaas(), JIT));
    }

    @Test
    void rejectsMissingOrMalformedInputsWithoutActivating() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            assertThrows(NullPointerException.class, () -> manager.load(null, SAFE_PROGRAM));
            assertThrows(NullPointerException.class, () -> manager.load("ok", null));
            assertThrows(IllegalArgumentException.class, () -> manager.load(" ", SAFE_PROGRAM));
            assertThrows(RuntimeException.class,
                    () -> manager.load("invalid.ores", "pub routine main(: void { return; }"));
            assertNull(manager.active());
            assertEquals(0, manager.liveGenerations());
        }
    }

    @Test
    void stagesAndDoesNotExecuteGuestMainUntilExplicitStart() {
        String invalidAtRuntime = """
                pub routine main(): void {
                  val values = arr[1];
                  val fail = values[99];
                  return;
                }
                """;
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var staged = manager.load("deferred.ores", invalidAtRuntime);
            assertFalse(staged.started());
            assertFalse(staged.closed());
            assertEquals(1, manager.liveGenerations());

            assertThrows(RuntimeException.class, staged::start);
            assertTrue(staged.started());
            assertTrue(staged.closed());
        }
    }

    @Test
    void generationStartIsSingleUse() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var generation = manager.load("once.ores", SAFE_PROGRAM);
            assertFalse(generation.started());
            assertDoesNotThrow(generation::start);
            assertTrue(generation.started());
            assertThrows(IllegalStateException.class, generation::start);
        }
    }

    @Test
    void generationHasContentDigestAndIndependentContextEvenOnIdenticalSource() throws Exception {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var first = manager.load("same.ores", SAFE_PROGRAM);
            var second = manager.load("same.ores", SAFE_PROGRAM);
            assertNotEquals(first.id(), second.id());
            assertNotSame(first.context(), second.context());
            assertEquals(first.sha256(), second.sha256());
            assertEquals(sha256(SAFE_PROGRAM), first.sha256());
            assertEquals("same.ores", first.codeUnitId());
            assertEquals(2, manager.liveGenerations());
            assertSame(second, manager.active("same.ores"));
            assertSame(second, manager.active());
        }
    }

    @Test
    void distinctUnitsRetainTheirOwnActiveGeneration() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var alpha = manager.load("alpha.ores", SAFE_PROGRAM);
            var beta = manager.load("beta.ores", SAFE_PROGRAM);
            var alphaV2 = manager.load("alpha.ores", version(2));
            Map<String, HotReloadManager.Generation> actives = manager.activeGenerations();

            assertEquals(2, actives.size());
            assertSame(alphaV2, actives.get("alpha.ores"));
            assertSame(beta, actives.get("beta.ores"));
            assertEquals(3, manager.liveGenerations());

            manager.retire(alpha.id());
            assertEquals(2, manager.liveGenerations());
            assertSame(alphaV2, manager.active("alpha.ores"));
            assertTrue(alpha.closed());
            assertFalse(beta.closed());
            assertFalse(alphaV2.closed());
        }
    }

    @Test
    void bothOldAndNewGenerationsCanExecuteBeforeDrain() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var old = manager.load("worker.ores", version(1));
            var newer = manager.load("worker.ores", version(2));
            assertDoesNotThrow(old::start);
            assertDoesNotThrow(newer::start);
            assertTrue(old.started());
            assertTrue(newer.started());
            assertFalse(old.closed());
            assertFalse(newer.closed());
            manager.retire(old.id());
            assertTrue(old.closed());
            assertFalse(newer.closed());
        }
    }

    @Test
    void retirementIsIdempotentAndDoesNotRetireAnotherUnit() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var first = manager.load("first.ores", SAFE_PROGRAM);
            var second = manager.load("second.ores", SAFE_PROGRAM);
            manager.retire(first.id());
            manager.retire(first.id());
            assertSame(second, manager.active("second.ores"));
            assertEquals(1, manager.liveGenerations());
            manager.retire(second.id());
            assertEquals(0, manager.liveGenerations());
            assertTrue(second.closed());
            assertNull(manager.active());
        }
    }

    @Test
    void closeReclaimsAllGenerationsAndIsIdempotent() {
        var manager = new HotReloadManager(IsolatePolicy.developer(), JIT);
        var first = manager.load("one.ores", SAFE_PROGRAM);
        var second = manager.load("two.ores", SAFE_PROGRAM);
        manager.close();
        manager.close();
        assertTrue(first.closed());
        assertTrue(second.closed());
        assertEquals(0, manager.liveGenerations());
        assertTrue(manager.activeGenerations().isEmpty());
    }

    @Test
    void rejectsForbiddenGuestCapabilityBeforeContextRegistration() {
        var supervisor = IsolatePolicy.developer();
        var guest = IsolatePolicy.strictFaas();
        try (var manager = new HotReloadManager(supervisor, guest, JIT)) {
            assertThrows(SecurityException.class, () -> manager.load("denied.ores", """
                    pub routine main(): void {
                      stdio.stdout.write(process.context_id);
                      return;
                    }
                    """));
            assertEquals(0, manager.liveGenerations());
            assertNull(manager.active());
        }
    }

    @Test
    void adversarialCodeUnitLengthRejectedAtAdmission() {
        try (var manager = HotReloadManager.forUntrustedActors(
                IsolatePolicy.developer(), JIT)) {
            String longName = "x".repeat(513);
            assertThrows(IllegalArgumentException.class,
                    () -> manager.load(longName, SAFE_PROGRAM));
            assertEquals(0, manager.liveGenerations());
        }
    }

    @Test
    void adversarialSourceSizeRejectedBeforeCompilation() {
        try (var manager = HotReloadManager.forUntrustedActors(
                IsolatePolicy.developer(), JIT)) {
            String tooLarge = SAFE_PROGRAM + " ".repeat(1_048_577);
            assertThrows(IllegalArgumentException.class,
                    () -> manager.load("big.ores", tooLarge));
            assertEquals(0, manager.liveGenerations());
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="polyglot.engine.IsolateLibrary", matches=".+")
    void adversarialLiveGenerationLimitIsBoundedAndRetirementFreesSlot() {
        try (var manager = HotReloadManager.forUntrustedActors(
                IsolatePolicy.developer(), JIT)) {
            var first = manager.load("one.ores", SAFE_PROGRAM);
            for (int i = 2; i <= 8; i++) {
                manager.load("unit-" + i + ".ores", SAFE_PROGRAM);
            }
            assertEquals(8, manager.liveGenerations());
            assertThrows(IllegalStateException.class,
                    () -> manager.load("ninth.ores", SAFE_PROGRAM));
            assertEquals(8, manager.liveGenerations());

            manager.retire(first.id());
            assertTrue(first.closed());
            assertDoesNotThrow(() -> manager.load("ninth.ores", SAFE_PROGRAM));
            assertEquals(8, manager.liveGenerations());
        }
    }

    @Test
    void allExecutionProfilesPreserveSourceHotLoadSemantics() {
        assertTrue(JIT.supportsSourceHotReload());
        assertTrue(ExecutionProfile.serverHybrid().supportsSourceHotReload());
        assertTrue(ExecutionProfile.mobileAot(ExecutionProfile.Platform.IOS)
                .supportsSourceHotReload());
        assertTrue(JIT.guestJitAllowed());
        assertFalse(ExecutionProfile.mobileAot(ExecutionProfile.Platform.IOS).guestJitAllowed());
        // This asserts declared profile semantics, not that an AOT binary ran.
    }

    @Test
    void successfulLoadAndStartKeepsGenerationLive() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var generation = manager.loadAndStart("start.ores", SAFE_PROGRAM);
            assertTrue(generation.started());
            assertFalse(generation.closed());
            assertSame(generation, manager.active("start.ores"));
        }
    }

    @Test
    void retiredGenerationCannotBeStartedAgain() {
        try (var manager = new HotReloadManager(IsolatePolicy.developer(), JIT)) {
            var generation = manager.load("retired.ores", SAFE_PROGRAM);
            manager.retire(generation.id());
            assertThrows(IllegalStateException.class, generation::start);
        }
    }

    private static String version(int number) {
        return "pub routine main(): void { val version = " + number + "; return; }";
    }

    private static String sha256(String content) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
