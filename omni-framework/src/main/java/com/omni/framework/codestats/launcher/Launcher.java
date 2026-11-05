package com.omni.framework.codestats.launcher;

import com.omni.framework.codestats.config.Config;

public interface Launcher {
    void launch(Config config) throws Exception;
}