package com.andrei1058.spigot.sidebar;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class SidebarLineAnimated extends SidebarLine {

    private final String[] frames;
    private final AtomicInteger index = new AtomicInteger(0);

    public SidebarLineAnimated(String[] frames) {
        if (frames == null || frames.length == 0) {
            throw new IllegalArgumentException("frames cannot be empty");
        }
        this.frames = Arrays.copyOf(frames, frames.length);
    }

    @Override
    public String getLine() {
        int i = Math.floorMod(index.get(), frames.length);
        return frames[i];
    }

    public void tick() {
        index.updateAndGet(current -> (current + 1) % frames.length);
    }
}

