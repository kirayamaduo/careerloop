package com.group1.career.utils;

import com.group1.career.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserObjectKeyPolicyTest {

    @Test
    void acceptsOnlyTheCurrentUsersNamespace() {
        assertEquals(
                "resumes/42/generated/file.pdf",
                UserObjectKeyPolicy.requireOwnedKey(
                        42L, "resumes", "resumes/42/generated/file.pdf"));
    }

    @Test
    void rejectsAnotherUsersKeyAndAbsoluteUrls() {
        assertThrows(BizException.class, () ->
                UserObjectKeyPolicy.requireOwnedKey(
                        42L, "resumes", "resumes/7/private.pdf"));
        assertThrows(BizException.class, () ->
                UserObjectKeyPolicy.requireOwnedKey(
                        42L, "avatars", "https://bucket.example/avatars/42/me.jpg"));
    }

    @Test
    void rejectsTraversalAndQueryDecoratedKeys() {
        assertThrows(BizException.class, () ->
                UserObjectKeyPolicy.requireOwnedKey(
                        42L, "resumes", "resumes/42/../7/private.pdf"));
        assertThrows(BizException.class, () ->
                UserObjectKeyPolicy.requireOwnedKey(
                        42L, "resumes", "resumes/42/file.pdf?signature=leak"));
    }
}
