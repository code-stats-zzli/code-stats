package com.codestats.lib.codestats.launcher;

import com.codestats.lib.codestats.config.Config;

public interface Launcher {
    void launch(Config config) throws Exception;
}