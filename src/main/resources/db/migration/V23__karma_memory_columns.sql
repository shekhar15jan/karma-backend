-- Fix: BaseEntity maps deleted_at and status columns; V22 tables omitted them.
ALTER TABLE karma_interaction_events ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE karma_interaction_events ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE karma_user_memories ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE karma_user_memories ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
