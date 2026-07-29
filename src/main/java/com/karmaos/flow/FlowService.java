package com.karmaos.flow;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FlowService {

    private final FlowRepository flowRepository;

    public FlowService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public List<FlowResponse> list() {
        return flowRepository.findByStatus("ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FlowResponse getById(UUID id) {
        Flow flow = flowRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));
        return toResponse(flow);
    }

    @Transactional
    public FlowResponse create(FlowRequest request) {
        Flow flow = new Flow();
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        flow.setCategory(request.getCategory());
        flow.setExecutionOrder(request.getExecutionOrder());
        flow.setEnabled(true);
        flow = flowRepository.save(flow);
        return toResponse(flow);
    }

    @Transactional
    public FlowResponse update(UUID id, FlowRequest request) {
        Flow flow = flowRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        flow.setCategory(request.getCategory());
        flow.setExecutionOrder(request.getExecutionOrder());
        flow = flowRepository.save(flow);
        return toResponse(flow);
    }

    @Transactional
    public void delete(UUID id) {
        Flow flow = flowRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));
        flow.softDelete();
        flowRepository.save(flow);
    }

    private FlowResponse toResponse(Flow f) {
        return FlowResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .category(f.getCategory())
                .executionOrder(f.getExecutionOrder())
                .enabled(f.isEnabled())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
