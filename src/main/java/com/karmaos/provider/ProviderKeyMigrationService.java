package com.karmaos.provider;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ProviderKeyMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ProviderKeyMigrationService.class);

    private final ProviderRepository providerRepository;
    private final ProviderKeyCipher keyCipher;

    public ProviderKeyMigrationService(ProviderRepository providerRepository, ProviderKeyCipher keyCipher) {
        this.providerRepository = providerRepository;
        this.keyCipher = keyCipher;
    }

    @PostConstruct
    @Transactional
    public void migrate() {
        List<Provider> providers = providerRepository.findAll();
        int migrated = 0;
        for (Provider provider : providers) {
            byte[] stored = provider.getEncryptedApiKey();
            if (stored == null || stored.length == 0 || keyCipher.isEncrypted(stored)) {
                continue;
            }
            String plaintext = new String(stored, StandardCharsets.UTF_8);
            if (plaintext.isBlank()) {
                log.warn("Skipping provider {} with empty legacy key", provider.getName());
                continue;
            }
            provider.setEncryptedApiKey(keyCipher.encrypt(plaintext));
            providerRepository.save(provider);
            migrated++;
            log.info("Re-encrypted legacy plaintext key for provider {}", provider.getName());
        }
        if (migrated > 0) {
            log.info("Migrated {} provider key(s) from plaintext to encrypted format", migrated);
        } else {
            log.info("No legacy plaintext provider keys found");
        }
    }
}
