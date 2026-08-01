-- Trim publishing channel catalog to the curated set:
-- YouTube, LinkedIn, X (Twitter), Facebook, Instagram.
DELETE FROM providers
WHERE provider_type = 'PUBLISHING'
  AND name IN ('TikTok', 'Pinterest', 'Threads', 'Snapchat', 'WordPress', 'Blogger', 'Medium', 'Substack', 'Reddit', 'Tumblr');
