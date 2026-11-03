package com.omni.framework.unzip;

import java.nio.file.Path;

public final class PathTraversalGuard {
    private PathTraversalGuard() {}

    public static Path resolveSafe(Path root, String entryName) throws ExtractException {
        if (entryName == null || entryName.trim().isEmpty())
            throw new ExtractException("非法条目名：空");
        String normalized = entryName.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty()) throw new ExtractException("非法条目名：空");

        for (String seg : normalized.split("/")) {
            if ("..".equals(seg))
                throw new ExtractException("非法条目名（路径穿越）: " + entryName);
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root))
            throw new ExtractException("非法条目名（越界）: " + entryName);
        return resolved;
    }
}