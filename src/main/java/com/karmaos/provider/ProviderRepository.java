package com.karmaos.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    List<Provider> findByStatus(String status);

    List<Provider> findByProviderType(String providerType);
}
