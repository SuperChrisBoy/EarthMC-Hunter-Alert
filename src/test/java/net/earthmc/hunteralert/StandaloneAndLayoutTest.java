package net.earthmc.hunteralert;

import net.earthmc.hunteralert.api.HudObstacles;
import net.earthmc.hunteralert.api.HudObstacles.Rect;
import net.earthmc.hunteralert.data.DirectEarthMcDataProvider;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneAndLayoutTest {
    @Test void movesAwayFromMapAndAddonInfoAtEitherCorner() {
        for(Rect obstacle:List.of(new Rect(4,4,255,305),new Rect(541,4,255,305))) {
            var result=HudObstacles.place(800,450,240,130,obstacle.x(),12,List.of(obstacle));
            assertNotNull(result); assertFalse(result.overlaps(obstacle));
            assertTrue(result.right()<=800 && result.bottom()<=450);
        }
    }
    @Test void preservesFreePreferenceAndDeclinesImpossibleLayout() {
        assertEquals(new Rect(15,25,100,50),HudObstacles.place(800,450,100,50,15,25,List.of()));
        assertNull(HudObstacles.place(200,150,100,50,0,0,List.of(new Rect(0,0,200,150))));
    }
    @Test void validatesMapBeforeDeclaringSomeoneHidden() {
        assertThrows(RuntimeException.class,() -> DirectEarthMcDataProvider.parseFeed("{}",1));
        assertThrows(RuntimeException.class,() -> DirectEarthMcDataProvider.parseFeed("{\"players\":[{\"name\":\"A\",\"world\":\"earth\"}]}",1));
        assertTrue(DirectEarthMcDataProvider.parseFeed("{\"players\":[]}",1).healthy());
        assertEquals("minecraft_overworld",DirectEarthMcDataProvider.world("earth"));
        assertNotEquals(DirectEarthMcDataProvider.world("moon"),DirectEarthMcDataProvider.world("earth"));
    }
    @Test void directHttpHandlesProfilesOutlaws304FailureAndDisconnect() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger mode=new AtomicInteger(200),requests=new AtomicInteger(); AtomicReference<String> query=new AtomicReference<>();
        AtomicReference<String> town=new AtomicReference<>("Example");
        server.createContext("/",exchange -> {
            requests.incrementAndGet(); String path=exchange.getRequestURI().getPath();
            int status=path.equals("/map") ? mode.get() : 200;
            String body=switch(path) {
                case "/map" -> "{\"players\":[{\"name\":\"Hunter\",\"uuid\":\"123456781234123412341234567890ab\",\"world\":\"minecraft_overworld\",\"x\":42,\"z\":84}]}";
                case "/towns" -> exchange.getRequestMethod().equals("GET") ? "[{\"name\":\"Example\"}]" : "[{\"name\":\"Example\",\"nation\":{\"name\":\"Nation\"},\"coordinates\":{\"spawn\":{\"world\":\"earth\",\"x\":100,\"z\":200}},\"outlaws\":[{\"name\":\"Hunter\"},{\"name\":\"Hunter\"}]}]";
                case "/players" -> "[{\"name\":\"Hunter\",\"town\":{\"name\":\""+town.get()+"\"},\"nation\":{\"name\":\"Nation\"}}]";
                default -> "[]";
            };
            if(exchange.getRequestMethod().equals("POST")) query.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("ETag","fixture");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,status==304 ? -1 : bytes.length);
            if(status!=304) exchange.getResponseBody().write(bytes);
            exchange.close();
        }); server.start();
        URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
        var provider=new DirectEarthMcDataProvider(base.resolve("map"),base);
        try {
            provider.tick(System.currentTimeMillis(),new HunterAlertConfig());
            await(() -> provider.snapshot().intelligenceAt()>0);
            assertEquals(1,provider.snapshot().outlawCounts().get("hunter"));
            assertEquals("Nation",provider.snapshot().profiles().get("hunter").nation());
            assertEquals("minecraft_overworld",provider.snapshot().towns().getFirst().anchor().world());
            assertTrue(query.get().contains("query"));
            long received=provider.snapshot().feed().receivedAt(); mode.set(304);
            provider.tick(System.currentTimeMillis()+4000,new HunterAlertConfig());
            await(() -> provider.snapshot().feed().receivedAt()>received);
            assertEquals(1,provider.snapshot().feed().players().size());
            mode.set(503); provider.tick(System.currentTimeMillis()+8000,new HunterAlertConfig());
            await(() -> !provider.snapshot().feed().healthy());
            assertEquals(1,provider.snapshot().feed().players().size());
            town.set("ChangedTown");
            provider.tick(System.currentTimeMillis()+61_000,new HunterAlertConfig());
            await(() -> provider.snapshot().profiles().containsKey("hunter") && provider.snapshot().profiles().get("hunter").town().equals("ChangedTown"));
            provider.reset(); assertFalse(provider.snapshot().feed().healthy()); assertTrue(provider.snapshot().feed().players().isEmpty());
            int before=requests.get(); Thread.sleep(150); assertEquals(before,requests.get());
        } finally { provider.close(); server.stop(0); }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+10_000_000_000L;
        while(!condition.getAsBoolean() && System.nanoTime()<until) Thread.sleep(20);
        assertTrue(condition.getAsBoolean(),"Timed out waiting for provider");
    }
}
