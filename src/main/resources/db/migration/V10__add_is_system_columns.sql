-- Mark seeded system resources as protected from deletion.
-- Seeded rows use deterministic UUIDs that gen_random_uuid() will never produce.

ALTER TABLE public.agents ADD COLUMN is_system boolean NOT NULL DEFAULT false;
ALTER TABLE public.skills ADD COLUMN is_system boolean NOT NULL DEFAULT false;
ALTER TABLE public.flows ADD COLUMN is_system boolean NOT NULL DEFAULT false;

UPDATE public.agents SET is_system = true
WHERE id::text ~ '^00000000-0000-0000-0000-00000000a[0-9a-f]{3}$';

UPDATE public.skills SET is_system = true
WHERE id::text ~ '^[ad]0000000-0000-0000-0000-0000000000[0-9a-f]{2}$';

UPDATE public.flows SET is_system = true
WHERE id::text ~ '^00000000-0000-0000-0000-00000000f0[0-9a-f]{2}$';