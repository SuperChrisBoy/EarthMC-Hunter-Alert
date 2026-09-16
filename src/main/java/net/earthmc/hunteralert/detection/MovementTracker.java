package net.earthmc.hunteralert.detection;

import net.earthmc.hunteralert.model.Models.*;
import net.earthmc.hunteralert.config.HunterAlertConfig;
import java.util.*;

public final class MovementTracker {
    private record Sample(Position position, long at) {}
    public record Result(Trend trend, double change, long millis) {}
    private final Map<String, Deque<Sample>> samples = new HashMap<>();
    public Result update(MapPlayer player, Position local, long now, HunterAlertConfig config) {
        String id = player.identity().key();
        if (!config.movement || now-player.updatedAt() > config.staleSeconds*1000L) {
            samples.remove(id); return new Result(Trend.UNKNOWN,0,0);
        }
        var queue = samples.computeIfAbsent(id, k -> new ArrayDeque<>());
        if (!queue.isEmpty() && (!queue.getLast().position.world().equals(player.position().world()) || player.updatedAt()-queue.getLast().at > config.staleSeconds*1000L)) queue.clear();
        if (queue.isEmpty() || player.updatedAt() > queue.getLast().at) queue.addLast(new Sample(player.position(),player.updatedAt()));
        while (queue.size() > 1 && (now-queue.getFirst().at > config.movementWindowSeconds*1000L || queue.size() > 64)) queue.removeFirst();
        if (queue.size() < 2) return new Result(Trend.UNKNOWN,0,0);
        Sample first = queue.getFirst(), last = queue.getLast();
        long elapsed = last.at-first.at;
        // Both positions relative to the SAME current local position: local travel is not hunter motion.
        var before = local.distance(first.position); var after = local.distance(last.position);
        if (elapsed < 2000 || before.isEmpty() || after.isEmpty()) return new Result(Trend.UNKNOWN,0,0);
        double change = after.getAsDouble()-before.getAsDouble();
        Trend trend = Math.abs(change) < config.minimumMovement ? Trend.STATIONARY : change < 0 ? Trend.APPROACHING : Trend.MOVING_AWAY;
        return new Result(trend,change,elapsed);
    }
    public void retain(Set<String> ids) { samples.keySet().retainAll(ids); }
    public void clear() { samples.clear(); }
}
