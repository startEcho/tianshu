package com.chenluo.laborchestrationservice.service;

import com.chenluo.laborchestrationservice.domain.LabLaunchOutboxEntity;
import com.chenluo.laborchestrationservice.model.LabLaunchCommand;
import com.chenluo.laborchestrationservice.repository.LabLaunchOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class LabLaunchOutboxRelay {

    private static final Logger logger = LoggerFactory.getLogger(LabLaunchOutboxRelay.class);

    private final LabLaunchOutboxRepository labLaunchOutboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public LabLaunchOutboxRelay(
            LabLaunchOutboxRepository labLaunchOutboxRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.labLaunchOutboxRepository = labLaunchOutboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${platform.launch.outbox.fixed-delay:1000}")
    @Transactional
    public void publishPendingLaunchCommands() {
        List<LabLaunchOutboxEntity> pendingMessages = labLaunchOutboxRepository.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (LabLaunchOutboxEntity pendingMessage : pendingMessages) {
            try {
                LabLaunchCommand command = objectMapper.readValue(pendingMessage.getPayload(), LabLaunchCommand.class);
                rabbitTemplate.convertAndSend(pendingMessage.getExchangeName(), pendingMessage.getRoutingKey(), command);
                pendingMessage.setPublishedAt(OffsetDateTime.now());
                labLaunchOutboxRepository.save(pendingMessage);
            } catch (JsonProcessingException ex) {
                logger.error("Failed to deserialize launch outbox payload for instance {}.", pendingMessage.getInstanceId(), ex);
            } catch (RuntimeException ex) {
                logger.warn("Failed to publish launch command for instance {}. Will retry.", pendingMessage.getInstanceId(), ex);
            }
        }
    }
}
