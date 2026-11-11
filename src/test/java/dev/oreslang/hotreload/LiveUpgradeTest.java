package dev.oreslang.hotreload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
final class LiveUpgradeTest {
    @Test @Timeout(90) void statefulGuestDrainsAcrossCutoverAndFailedStage() throws Exception {
        LiveUpgradeProof.main(new String[]{"jvm"});
    }
}
