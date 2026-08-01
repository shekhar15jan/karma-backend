package com.karmaos.prompt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID> {

    List<Prompt> findByStatus(String status);

    List<Prompt> findByStatusIn(Collection<String> statuses);

    List<Prompt> findByCategoryAndStatus(String category, String status);

    Optional<Prompt> findByIdAndStatusNot(UUID id, String status);
}
