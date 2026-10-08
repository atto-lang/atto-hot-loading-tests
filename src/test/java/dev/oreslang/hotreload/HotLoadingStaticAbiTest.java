package dev.oreslang.hotreload;

import dev.oreslang.compiler.IncrementalCompiler;
import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compiled-unit hot loads exercise the *real* Oreslang incremental compiler.
 * The compiler gives static ABI digests; the current loader does NOT yet
 * enforce ABI compatibility at the activation boundary.
 */
@Timeout(60)
final class HotLoadingStaticAbiTest {
    private static final String UNIT = "service.ores";
    private static final String V1 = "pub routine main(): void { val revision = 1; return; }";
    private static final String V2_BODY_ONLY =
            "pub routine main(): void { val revision = 2; return; }";
    private static final String V3_PUBLIC_ABI = """
            pub routine main(): void { val revision = 3; return; }
            pub routine helper(): void { return; }
            """;

    @Test
    void unchangedSourceIsReusedByCompilerButNotByHotLoadContext() {
        var compiler = new IncrementalCompiler();
        var first = compiler.compile(Map.of(UNIT, V1));
        var second = compiler.compile(Map.of(UNIT, V1));
        assertTrue(first.rebuilt(UNIT));
        assertTrue(second.reused(UNIT));
        assertSame(first.units().get(UNIT), second.units().get(UNIT));

        try (var manager = new HotReloadManager(
                IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var a = manager.load(first.units().get(UNIT));
            var b = manager.load(second.units().get(UNIT));
            assertNotSame(a.context(), b.context());
            assertNotEquals(a.id(), b.id());
            assertEquals(a.sha256(), b.sha256());
            assertDoesNotThrow(a::start);
            assertDoesNotThrow(b::start);
        }
    }

    @Test
    void implementationOnlyEditChangesContentHashButKeepsStaticAbi() {
        var compiler = new IncrementalCompiler();
        var first = compiler.compile(Map.of(UNIT, V1)).units().get(UNIT);
        var build = compiler.compile(Map.of(UNIT, V2_BODY_ONLY));
        var updated = build.units().get(UNIT);
        assertTrue(build.rebuilt(UNIT));
        assertNotEquals(first.sourceDigest(), updated.sourceDigest());
        assertEquals(first.abiDigest(), updated.abiDigest());
    }

    @Test
    void AddingPublicRoutineChangesAbiAndRebuildsCompiledUnit() {
        var compiler = new IncrementalCompiler();
        var before = compiler.compile(Map.of(UNIT, V1)).units().get(UNIT);
        var rebuild = compiler.compile(Map.of(UNIT, V3_PUBLIC_ABI));
        var after = rebuild.units().get(UNIT);
        assertTrue(rebuild.rebuilt(UNIT));
        assertNotEquals(before.abiDigest(), after.abiDigest());
    }

    @Test
    void previouslyCompiledVersionRemainsExecutableAfterCompilerCacheUpdates() {
        var compiler = new IncrementalCompiler();
        var oldCode = compiler.compile(Map.of(UNIT, V1)).units().get(UNIT);
        var newCode = compiler.compile(Map.of(UNIT, V2_BODY_ONLY)).units().get(UNIT);

        try (var manager = new HotReloadManager(
                IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var v1 = manager.load(oldCode);
            var v2 = manager.load(newCode);
            assertDoesNotThrow(v1::start);
            assertDoesNotThrow(v2::start);
            assertFalse(v1.closed());
            assertFalse(v2.closed());
            assertNotSame(v1.context(), v2.context());
        }
    }

    @Test
    void revertingToOldCompiledImageStillRequiresANewGeneration() {
        var compiler = new IncrementalCompiler();
        var v1Image = compiler.compile(Map.of(UNIT, V1)).units().get(UNIT);
        var v2Image = compiler.compile(Map.of(UNIT, V2_BODY_ONLY)).units().get(UNIT);
        try (var manager = new HotReloadManager(
                IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var v1 = manager.load(v1Image);
            var v2 = manager.load(v2Image);
            var reverted = manager.load(v1Image);
            assertEquals(v1.sha256(), reverted.sha256());
            assertNotSame(v1.context(), reverted.context());
            assertNotEquals(v1.id(), reverted.id());
            assertSame(reverted, manager.active(UNIT));
            assertDoesNotThrow(v1::start);
            assertDoesNotThrow(v2::start);
            assertDoesNotThrow(reverted::start);
            // This is restaging, not an atomic production rollback protocol.
        }
    }

    @Test
    void failedRecompileDoesNotLoseLastKnownGoodCompiledUnit() {
        var compiler = new IncrementalCompiler();
        var successful = compiler.compile(Map.of(UNIT, V1)).units().get(UNIT);
        assertThrows(RuntimeException.class,
                () -> compiler.compile(Map.of(UNIT, "pub routine main(: void {}")));
        var retried = compiler.compile(Map.of(UNIT, V1));
        assertTrue(retried.reused(UNIT));
        assertSame(successful, retried.units().get(UNIT));
    }

    @Test
    void modifyingOneUnitRebuildsOnlyItsOwnIndependentCompilationUnit() {
        var compiler = new IncrementalCompiler();
        var previous = compiler.compile(Map.of("a.ores", V1, "b.ores", V1));
        var next = compiler.compile(Map.of("a.ores", V2_BODY_ONLY, "b.ores", V1));
        assertTrue(next.rebuilt("a.ores"));
        assertTrue(next.reused("b.ores"));
        assertNotSame(previous.units().get("a.ores"), next.units().get("a.ores"));
        assertSame(previous.units().get("b.ores"), next.units().get("b.ores"));
    }

    @Test
    void compiledUnitCarriesTheExactSourceDigestAndCodeUnitIdentity() {
        var compiler = new IncrementalCompiler();
        var image = compiler.compile(Map.of(UNIT, V1)).units().get(UNIT);
        try (var manager = new HotReloadManager(
                IsolatePolicy.developer(), ExecutionProfile.serverJit())) {
            var generation = manager.load(image);
            assertEquals(UNIT, generation.codeUnitId());
            assertEquals(image.sourceDigest(), generation.sha256());
            assertSame(generation, manager.active(UNIT));
            assertDoesNotThrow(generation::start);
        }
    }
}
