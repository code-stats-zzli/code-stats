package com.codestats.framework.codestats.launcher;

import com.codestats.framework.codestats.config.Config;
import com.codestats.framework.codestats.pipeline.Pipeline;

public class ConsoleLauncher implements Launcher {
    @Override
    public void launch(Config config) {
        Pipeline.createDefault(config).execute(config.getRootDir());
    }
}