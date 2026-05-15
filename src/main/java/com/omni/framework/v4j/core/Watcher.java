package com.omni.framework.v4j.core;

public class Watcher {
    private final Runnable getter;

    public Watcher(Runnable getter) {
        this.getter = getter;
        run();
    }

    public void run() {
        Dep.setTarget(this);
        try {
            getter.run();
        } finally {
            Dep.setTarget(null);
        }
    }

    public void update() {
        run();
    }
}