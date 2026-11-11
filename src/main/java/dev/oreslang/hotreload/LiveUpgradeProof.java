package dev.oreslang.hotreload;

import dev.oreslang.runtime.ExecutionProfile;
import dev.oreslang.runtime.HotReloadManager;
import dev.oreslang.runtime.IsolatePolicy;
import dev.oreslang.runtime.NativeBuildMode;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Test driver only: guest owns actor state, messages, network read and teardown. */
public final class LiveUpgradeProof {
    static String fixture(String name) throws Exception {
        try (var input = LiveUpgradeProof.class.getResourceAsStream("/atto/" + name)) {
            if (input == null) throw new AssertionError("missing guest fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    static void rejects(Runnable work, String reason) {
        try { work.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(reason);
    }
    static ServerSocket barrier() throws Exception {
        var server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(15000);
        return server;
    }
    static void release(Socket socket) throws Exception {
        socket.getOutputStream().write("release\n".getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
        socket.setSoTimeout(15000);
        check(socket.getInputStream().read() == -1, "guest socket not physically closed");
    }
    public static void main(String[] args) throws Exception {
        boolean nativeRun = "native".equals(args.length == 0 ? "jvm" : args[0]);
        check((NativeBuildMode.kind() == NativeBuildMode.Kind.AOT) == nativeRun,
                "requested platform differs from actual build kind");
        String startupFailure = fixture("crash-on-start.atto");
        var profile = nativeRun ? ExecutionProfile.parse("aot", "server") : ExecutionProfile.serverJit();
        System.out.println("BUILD_KIND=" + NativeBuildMode.kind());
        try (var manager = new HotReloadManager(IsolatePolicy.developer().withCapabilities(IsolatePolicy.Capability.NETWORK), profile);
             var pool = Executors.newFixedThreadPool(3);
             var oldGate = barrier(); var newGate = barrier()) {
            var old = manager.reloadAndStart("service.atto", fixture("counter-v1.atto"));
            check(manager.serving("service.atto") == old, "v1 not serving");
            var operation = manager.pinServing("service.atto");
            var pending = pool.submit(() -> {
                try (operation) { return operation.call("request", oldGate.getLocalPort()).asInt(); }
            });
            try (var oldSocket = oldGate.accept()) {
                check(!pending.isDone(), "v1 request must retain actor state between mailbox turns");
                // Closing the caller lease must not release the internal physical call pin.
                operation.close();
                var next = manager.reloadAndStart("service.atto", fixture("counter-v2.atto"));
                check(manager.serving("service.atto") == next, "v2 not serving");
                check(!old.closed() && manager.liveGenerations() == 2, "old context lost during cutover");
                rejects(() -> manager.retire(old.id()), "retired live v1 operation");
                check(!old.closed(), "physical context closed while actor awaited network");
                var fresh = pool.submit(() -> {
                    try (var op = manager.pinServing("service.atto")) {
                        check(op.generation() == next, "new work routed to old generation");
                        return op.call("request", newGate.getLocalPort()).asInt();
                    }
                });
                try (var newSocket = newGate.accept()) { release(newSocket); }
                int observed = fresh.get(15, TimeUnit.SECONDS);
                check(observed == 1214, "v2 guest state/turns/teardown wrong: " + observed);
                check(!pending.isDone() && !old.closed(), "new completion released old resources");
                System.out.println("CUTOVER new=1214 old=in-flight old-context=open");
                rejects(() -> manager.reloadAndStart("service.atto", "not valid source"), "malformed stage admitted");
                dev.oreslang.compiler.OresCompiler.validateForIsolate(startupFailure,
                        IsolatePolicy.developer());
                rejects(() -> manager.reloadAndStart("service.atto",
                        startupFailure),
                        "failed startup admitted");
                check(manager.serving("service.atto") == next && manager.liveGenerations() == 2,
                        "failed candidate changed service or leaked context");
                var afterFailure = pool.submit(() -> {
                    try (var op = manager.pinServing("service.atto")) {
                        check(op.generation() == next, "rollback route changed");
                        return op.call("request", newGate.getLocalPort()).asInt();
                    }
                });
                try (var retrySocket = newGate.accept()) { release(retrySocket); }
                check(afterFailure.get(15, TimeUnit.SECONDS) == 1214, "service unusable after staging failure");
                System.out.println("ROLLBACK malformed+startup rejected service=1214 live-contexts=2");
                release(oldSocket);
                check(pending.get(15, TimeUnit.SECONDS) == 102, "old state/code/teardown changed after cutover");
                manager.retire(old.id());
                check(old.closed() && manager.liveGenerations() == 1, "old context not physically retired");
                rejects(() -> old.context().eval(old.source()), "retired Graal context executable");
                rejects(() -> operation.call("request", oldGate.getLocalPort()), "released operation reused");
                System.out.println("RETIRE old=102 guest-done+teardown observed context=closed live-contexts=1");
                manager.retire(next.id());
                check(next.closed() && manager.liveGenerations() == 0, "final resource leak");
            }
        }
        System.out.println("LIVE_UPGRADE_PROOF_PASS");
    }
}
