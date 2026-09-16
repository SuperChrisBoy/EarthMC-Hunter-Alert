package net.earthmc.hunteralert.platform;

import com.sun.net.httpserver.HttpServer;
import net.earthmc.hunteralert.api.EmcMapAddonBridge;
import net.earthmc.hunteralert.model.Models.Feed;
import net.townymap.TownyMapConfig;
import net.townymap.api.SquaremapApiClient;
import org.slf4j.LoggerFactory;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback fixtures exercise the real upstream parser and applied mixins in development only. */
final class FeedIntegrationProbe {
    static CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            HttpServer server=null; SquaremapApiClient api=null;
            try {
                if(EmcMapAddonDataProvider.sharedEarthApi==null) throw new AssertionError("Shared API constructor bridge did not initialize");
                AtomicInteger scenario=new AtomicInteger();
                server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
                server.createContext("/players",exchange -> {
                    int code=switch(scenario.get()) { case 1 -> 304; case 2 -> 503; default -> 200; };
                    String body=switch(scenario.get()) {
                        case 0 -> "{\"players\":[{\"name\":\"FixtureHunter\",\"uuid\":\"00000000000000000000000000000002\",\"world\":\"minecraft_overworld\",\"x\":100,\"z\":-200}]}";
                        case 3 -> "<html>upstream error</html>";
                        default -> "{\"players\":[]}";
                    };
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("ETag","fixture-v1");
                    exchange.sendResponseHeaders(code,code==304?-1:bytes.length);
                    if(code!=304) exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
                server.start();
                String url="http://127.0.0.1:"+server.getAddress().getPort()+"/players";
                api=new SquaremapApiClient(new TownyMapConfig() { @Override public String playersUrl() { return url; } });
                EmcMapAddonBridge.reset();
                Feed first=poll(api);
                require(first.healthy() && first.players().size()==1,"valid payload");
                require(first.players().getFirst().position().world().equals("minecraft_overworld"),"world identity");
                api.onWorldChanged();
                scenario.set(1); Feed unchanged=poll(api);
                require(unchanged.healthy() && unchanged.players().size()==1,"304 retains players");
                scenario.set(2); require(!poll(api).healthy(),"503 is unavailable");
                scenario.set(3); require(!poll(api).healthy(),"malformed is unavailable");
                scenario.set(1); require(poll(api).players().size()==1,"304 after failure preserves last valid players");
                scenario.set(4); Feed empty=poll(api);
                require(empty.healthy() && empty.players().isEmpty(),"healthy empty feed");
                LoggerFactory.getLogger("HunterAlertSmoke").info("FEED_BRIDGE_PASS: valid, world, 304, 503, malformed, empty; shared API hook present");
            } catch(Exception e) { throw new CompletionException(e); }
            finally { if(api!=null) api.stop(); if(server!=null) server.stop(0); EmcMapAddonBridge.reset(); }
        });
    }
    private static Feed poll(SquaremapApiClient api) throws InterruptedException {
        long before=EmcMapAddonBridge.latest().receivedAt(), deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            Feed feed=EmcMapAddonBridge.latest();
            if(feed.receivedAt()>before) return feed;
            api.tickPlayers();
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for addon feed completion");
    }
    private static void require(boolean condition,String description) { if(!condition) throw new AssertionError(description); }
}
