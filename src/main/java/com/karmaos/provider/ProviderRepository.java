package com.karmaos.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    List<Provider> findByStatus(String status);

    Optional<Provider> findByIdAndStatus(UUID id, String status);
}
