package net.earthmc.hunteralert.detection;

import java.util.*;
import java.util.function.ToIntFunction;

/** Complete, word-wrapped text and bounded pages; no ellipsis or font shrinking. */
public final class WrappedHud {
    public record Page<T>(List<T> lines,int number,int count) {}
    public static List<String> wrap(String text,int width,ToIntFunction<String> measure) {
        List<String> result=new ArrayList<>();
        for(String paragraph:text.split("\\R",-1)) {
            String remaining=paragraph.strip();
            if(remaining.isEmpty()) { result.add(""); continue; }
            while(!remaining.isEmpty()) {
                if(measure.applyAsInt(remaining)<=width) { result.add(remaining); break; }
                int end=0;
                while(end<remaining.length()) {
                    int next=remaining.offsetByCodePoints(end,1);
                    if(measure.applyAsInt(remaining.substring(0,next))>width) break;
                    end=next;
                }
                // A single glyph cannot be reduced further without changing the user's font scale.
                if(end==0) end=remaining.offsetByCodePoints(0,1);
                int space=remaining.lastIndexOf(' ',end);
                if(space>0) end=space;
                result.add(remaining.substring(0,end));
                remaining=remaining.substring(end).stripLeading();
            }
        }
        return List.copyOf(result);
    }
    public static <T> Page<T> page(List<T> pinned,List<T> log,int capacity,long elapsed) {
        capacity=Math.max(1,capacity);
        List<T> fixed=pinned, scrolling=log;
        if(pinned.size()>=capacity) {
            fixed=List.of(); scrolling=new ArrayList<>(pinned); scrolling.addAll(log);
        }
        int room=capacity-fixed.size();
        int count=Math.max(1,(scrolling.size()+room-1)/room);
        // Give longer pages more reading time. Keep the first page visible initially.
        long duration=Math.max(8000L,room*2500L);
        int index=(int)(Math.max(0,elapsed)/duration%count);
        int start=Math.min(scrolling.size(),index*room);
        List<T> lines=new ArrayList<>(fixed);
        lines.addAll(scrolling.subList(start,Math.min(scrolling.size(),start+room)));
        return new Page<>(List.copyOf(lines),index+1,count);
    }
    private WrappedHud() {}
}
