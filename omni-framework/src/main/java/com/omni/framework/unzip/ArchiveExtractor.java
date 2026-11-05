package com.omni.framework.unzip;

import java.io.IOException;

public interface ArchiveExtractor {
    ArchiveType getType();
    default boolean supports(byte[] head, String fileName) { return true; }
    ExtractResult extract(ExtractRequest request) throws ExtractException, IOException;
    default int getPriority() { return 100; }
}