-- V7: Route the Music Agent to Google Music (Lyria).
-- The Music Agent was created without a provider; now that a CONNECTED
-- google-music provider exists, assign it so MUSIC_GENERATION steps prefer Lyria.

UPDATE agents
   SET default_provider_id = (
           SELECT id
             FROM providers
            WHERE provider_type = 'google-music'
              AND deleted_at IS NULL
            LIMIT 1
       )
 WHERE name = 'Music Agent'
   AND status = 'ACTIVE'
   AND default_provider_id IS NULL;
