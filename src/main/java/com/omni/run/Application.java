package com.omni.run;

import com.omni.framework.codestats.bootstrap.CodestatsEngine;

public class Application {
    public static void main(String[] args) {
        CodestatsEngine.run(Application.class, args);
    }
}