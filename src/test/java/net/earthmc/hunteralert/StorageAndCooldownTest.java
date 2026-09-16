package net.earthmc.hunteralert;

import net.earthmc.hunteralert.alert.AlertManager;
import net.earthmc.hunteralert.config.LocalStore;
import net.earthmc.hunteralert.model.Models.Alert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageAndCooldownTest {
    @TempDir Path directory;
    @Test void cooldownSuppressesDuplicatesAndAllowsLaterTransitions() {
        var manager=new AlertManager();
        assertTrue(manager.accept(new Alert(1000,"a","","",1),30_000));
        assertFalse(manager.accept(new Alert(2000,"a","","",1),30_000));
        assertTrue(manager.accept(new Alert(2000,"b","","",1),30_000));
        assertTrue(manager.accept(new Alert(32_000,"a","","",1),30_000));
    }
    @Test void configAndBoundedHistoryPersist() throws Exception {
        try(var store=new LocalStore(directory)) {
            awaitReady(store);
            var c=store.editableCopy(); c.minimumOutlawTowns=17; c.historyLimit=100; store.save(c);
            List<Alert> events=new ArrayList<>(); for(int i=0;i<150;i++) events.add(new Alert(i,"key"+i,"Test","message",1));
            store.append(events); assertEquals(100,store.history().size());
        }
        for(int i=0;i<100 && !Files.exists(directory.resolve("history.json"));i++) Thread.sleep(20);
        try(var reopened=new LocalStore(directory)) {
            awaitReady(reopened); assertEquals(17,reopened.config().minimumOutlawTowns); assertEquals(100,reopened.history().size()); assertEquals(50,reopened.history().getFirst().at());
        }
    }
    @Test void malformedConfigIsBackedUp() throws Exception {
        Files.writeString(directory.resolve("config.json"),"{ broken");
        try(var store=new LocalStore(directory)) { awaitReady(store); assertTrue(store.config().enabled); }
        try(var files=Files.list(directory)) { assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("config.json.invalid-"))); }
    }
    private static void awaitReady(LocalStore store) throws Exception { for(int i=0;i<100 && !store.ready();i++) Thread.sleep(20); assertTrue(store.ready()); }
}
