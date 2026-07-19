-- Permanent account erasure keeps only minimum anonymous audit evidence.
-- Raw user/admin ids and network fingerprints become nullable so the cleanup
-- transaction can unlink them before deleting the users row.

ALTER TABLE `account_deletion_log`
  MODIFY COLUMN `user_id` BIGINT NULL;

ALTER TABLE `account_deletion_log`
  ADD COLUMN `subject_hash` VARCHAR(64) NULL
    COMMENT 'HMAC-SHA256 internal subject token; raw user id is removed at purge';

ALTER TABLE `account_deletion_log`
  ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING | CANCELLED | COMPLETED';

ALTER TABLE `account_deletion_log`
  ADD COLUMN `cancelled_at` DATETIME NULL
    COMMENT 'time at which the user restored the account during the grace period';

ALTER TABLE `account_deletion_log`
  ADD COLUMN `completed_at` DATETIME NULL
    COMMENT 'time at which permanent personal-data erasure completed';

ALTER TABLE `admin_audit_log`
  MODIFY COLUMN `admin_id` BIGINT NULL;

CREATE INDEX `idx_deletion_log_subject_hash`
  ON `account_deletion_log` (`subject_hash`);
