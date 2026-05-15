package com.codestats.framework.codestats.launcher;

import com.codestats.framework.codestats.config.Config;

public interface Launcher {
    void launch(Config config) throws Exception;
}