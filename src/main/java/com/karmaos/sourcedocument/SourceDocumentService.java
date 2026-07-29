package com.karmaos.sourcedocument;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SourceDocumentService {

    private final SourceDocumentRepository repository;

    public SourceDocumentService(SourceDocumentRepository repository) {
        this.repository = repository;
    }

    public List<SourceDocumentResponse> listByMission(UUID missionId) {
        return repository.findByMissionId(missionId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SourceDocumentResponse getById(UUID id) {
        SourceDocument doc = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Source document not found: " + id));
        return toResponse(doc);
    }

    @Transactional
    public SourceDocumentResponse create(SourceDocumentRequest request) {
        SourceDocument doc = new SourceDocument();
        doc.setMissionId(request.getMissionId());
        doc.setFilename(request.getFilename());
        doc.setContent(request.getContent());
        doc.setFormat(request.getFormat() != null ? request.getFormat() : "MARKDOWN");
        doc.setSize(request.getContent() != null ? request.getContent().getBytes(StandardCharsets.UTF_8).length : 0);
        doc.setChecksum(computeChecksum(request.getContent()));
        doc = repository.save(doc);
        return toResponse(doc);
    }

    @Transactional
    public void delete(UUID id) {
        SourceDocument doc = repository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Source document not found: " + id));
        repository.delete(doc);
    }

    private String computeChecksum(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    private SourceDocumentResponse toResponse(SourceDocument d) {
        return SourceDocumentResponse.builder()
                .id(d.getId())
                .missionId(d.getMissionId())
                .filename(d.getFilename())
                .content(d.getContent())
                .format(d.getFormat())
                .size(d.getSize())
                .checksum(d.getChecksum())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
