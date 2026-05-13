package com.codestats.lib.codestats.launcher;

import com.codestats.lib.codestats.config.Config;
import com.codestats.lib.codestats.pipeline.Pipeline;

public class ConsoleLauncher implements Launcher {
    @Override
    public void launch(Config config) {
        Pipeline.createDefault(config).execute(config.getRootDir());
    }
}