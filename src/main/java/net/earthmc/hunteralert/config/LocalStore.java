package net.earthmc.hunteralert.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.earthmc.hunteralert.model.Models.Alert;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Coalesced atomic disk writes on one daemon executor; rendering only reads immutable lists. */
public final class LocalStore implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"HunterAlert-storage"); t.setDaemon(true); return t; });
    private final AtomicReference<HunterAlertConfig> config = new AtomicReference<>(new HunterAlertConfig());
    private final AtomicReference<List<Alert>> history = new AtomicReference<>(List.of());
    private final AtomicBoolean configDirty=new AtomicBoolean(), historyDirty=new AtomicBoolean();
    private final CompletableFuture<Void> loaded;
    public LocalStore(Path directory) {
        this.directory=directory;
        loaded=CompletableFuture.runAsync(this::load,io);
        io.scheduleWithFixedDelay(this::flush,1,1,TimeUnit.SECONDS);
    }
    public boolean ready() { return loaded.isDone(); }
    private void load() {
        try {
            Files.createDirectories(directory);
            Path file=directory.resolve("config.json");
            if (Files.exists(file)) {
                HunterAlertConfig loaded=GSON.fromJson(Files.readString(file),HunterAlertConfig.class);
                if (loaded == null) throw new IOException("Empty config");
                loaded.validate(); config.set(loaded);
            }
        } catch (Exception e) { recover("config.json",e); }
        try {
            Path file=directory.resolve("history.json");
            if (Files.exists(file)) {
                List<Alert> loaded=GSON.fromJson(Files.readString(file),new TypeToken<List<Alert>>(){}.getType());
                if (loaded != null) history.set(loaded.stream().filter(Objects::nonNull).skip(Math.max(0,loaded.size()-config.get().historyLimit)).toList());
            }
        } catch (Exception e) { recover("history.json",e); }
    }
    private void recover(String name,Exception error) {
        LoggerFactory.getLogger("HunterAlert").warn("Could not read {}; preserving a backup",name,error);
        try { Path file=directory.resolve(name); if (Files.exists(file)) Files.copy(file,directory.resolve(name+".invalid-"+System.currentTimeMillis()),StandardCopyOption.REPLACE_EXISTING); }
        catch(IOException e) { LoggerFactory.getLogger("HunterAlert").warn("Could not back up {}",name,e); }
    }
    public HunterAlertConfig config() { return config.get(); }
    public HunterAlertConfig editableCopy() { return config.get().copy(); }
    public void save(HunterAlertConfig value) { value.validate(); config.set(value.copy()); configDirty.set(true); }
    public List<Alert> history() { return history.get(); }
    public void append(List<Alert> events) {
        if (events.isEmpty()) return;
        history.updateAndGet(old -> { List<Alert> next=new ArrayList<>(old); next.addAll(events); return List.copyOf(next.subList(Math.max(0,next.size()-config.get().historyLimit),next.size())); });
        historyDirty.set(true);
    }
    private void flush() {
        if (configDirty.getAndSet(false)) try { write("config.json",config.get()); } catch(IOException e) { configDirty.set(true); warn(e); }
        if (historyDirty.getAndSet(false)) try { write("history.json",history.get()); } catch(IOException e) { historyDirty.set(true); warn(e); }
    }
    private void warn(Exception e) { LoggerFactory.getLogger("HunterAlert").warn("Could not persist local data",e); }
    private void write(String name,Object value) throws IOException {
        Path temp=directory.resolve(name+".tmp"), target=directory.resolve(name);
        Files.writeString(temp,GSON.toJson(value));
        try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public void close() {
        io.execute(this::flush); io.shutdown();
        // Shutdown only: allow the final worker write to finish before Minecraft exits the JVM.
        try { if(!io.awaitTermination(3,TimeUnit.SECONDS)) LoggerFactory.getLogger("HunterAlert").warn("Final storage flush is still pending"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
