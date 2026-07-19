-- Server-side JWT revocation generation.
-- Existing users and pre-deployment JWTs both map to generation zero, which
-- preserves rolling-deployment compatibility until a revocation event occurs.
ALTER TABLE `users`
  ADD COLUMN `auth_version` BIGINT NOT NULL DEFAULT 0 AFTER `status`;
