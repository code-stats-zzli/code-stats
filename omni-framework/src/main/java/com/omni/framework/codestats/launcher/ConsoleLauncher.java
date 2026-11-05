package com.omni.framework.codestats.launcher;

import com.omni.framework.codestats.config.Config;
import com.omni.framework.codestats.pipeline.Pipeline;

public class ConsoleLauncher implements Launcher {
    @Override
    public void launch(Config config) {
        Pipeline.createDefault(config).execute(config.getRootDir());
    }
}