package com.group1.career.utils;

import com.group1.career.exception.BizException;

/**
 * Ownership boundary for OSS object references accepted from a client.
 *
 * <p>New uploads are stored under {@code <kind>/<userId>/...}. A client may
 * persist only keys in its own namespace; absolute URLs, traversal and another
 * user's namespace are rejected before a presigned URL can ever be issued.</p>
 */
public final class UserObjectKeyPolicy {

    private UserObjectKeyPolicy() {}

    public static String requireOwnedKey(Long userId, String kind, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return rawKey;
        if (userId == null || userId <= 0 || kind == null || kind.isBlank()) {
            throw new BizException("Invalid object ownership context");
        }

        String key = rawKey.trim();
        String expectedPrefix = kind + "/" + userId + "/";
        if (!key.startsWith(expectedPrefix)
                || key.length() <= expectedPrefix.length()
                || key.contains("://")
                || key.contains("\\")
                || key.contains("..")
                || key.contains("//")
                || key.contains("?")
                || key.contains("#")
                || key.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f)) {
            throw new BizException("Object key does not belong to the current user");
        }
        return key;
    }
}
