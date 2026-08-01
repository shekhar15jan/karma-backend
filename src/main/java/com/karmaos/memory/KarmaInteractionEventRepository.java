package com.karmaos.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KarmaInteractionEventRepository extends JpaRepository<KarmaInteractionEvent, UUID> {

    List<KarmaInteractionEvent> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<KarmaInteractionEvent> findByIdAndUserId(UUID id, UUID userId);
}
