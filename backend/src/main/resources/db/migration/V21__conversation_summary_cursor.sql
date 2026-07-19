-- Make rolling assistant summaries incremental and safe under async overlap.
ALTER TABLE `conversation_summaries`
  ADD COLUMN `last_message_id` BIGINT NOT NULL DEFAULT 0 AFTER `tokens_consumed`,
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 AFTER `last_message_id`;

CREATE INDEX `idx_assistant_messages_session_msg`
  ON `assistant_messages` (`session_id`, `msg_id`);
