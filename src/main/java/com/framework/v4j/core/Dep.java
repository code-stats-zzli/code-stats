package com.framework.v4j.core;

import java.util.ArrayList;
import java.util.List;

public class Dep {
    private static final ThreadLocal<Watcher> targetHolder = new ThreadLocal<>();
    private final List<Watcher> subs = new ArrayList<>();

    public void depend() {
        Watcher w = targetHolder.get();
        if (w != null && !subs.contains(w)) {
            subs.add(w);
        }
    }

    public void notifyObservers() {
        for (Watcher w : subs) {
            w.update();
        }
    }

    public static void setTarget(Watcher w) {
        if (w != null) targetHolder.set(w);
        else targetHolder.remove();
    }

    public static Watcher getTarget() {
        return targetHolder.get();
    }
}