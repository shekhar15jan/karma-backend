package com.karmaos.llm.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmCacheRepository extends JpaRepository<LlmCache, String> {
}
