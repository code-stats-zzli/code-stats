package com.omni.business.assistance.systemmonitor.component;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

public abstract class AbstractSystemComponent implements SystemComponent {
    protected static final Logger log = LoggerFactory.getLogger(AbstractSystemComponent.class);

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public long getRefreshInterval() { return 1000L; }
}