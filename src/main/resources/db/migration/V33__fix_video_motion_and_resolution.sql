UPDATE settings
SET settings_data = jsonb_set(
      jsonb_set(settings_data, '{video.scene_motion}', '"animated"'),
      '{video.resolution}', '"1920x1080"'
    ),
    updated_at = now()
WHERE settings_data::text LIKE '%video.scene_motion%';