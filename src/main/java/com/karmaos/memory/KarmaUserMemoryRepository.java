package com.karmaos.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KarmaUserMemoryRepository extends JpaRepository<KarmaUserMemory, UUID> {

    List<KarmaUserMemory> findByUserIdOrderByConfidenceDesc(UUID userId);

    Optional<KarmaUserMemory> findByUserIdAndMemoryKey(UUID userId, String memoryKey);
}
