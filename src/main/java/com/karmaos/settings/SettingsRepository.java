package com.karmaos.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingsRepository extends JpaRepository<Settings, UUID> {

    Optional<Settings> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<Settings> findByWorkspaceId(UUID workspaceId);

    Optional<Settings> findByUserId(UUID userId);

    Optional<Settings> findFirstByOrderByUpdatedAtDesc();
}
