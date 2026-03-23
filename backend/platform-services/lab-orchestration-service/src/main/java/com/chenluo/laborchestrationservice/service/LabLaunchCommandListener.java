package com.chenluo.laborchestrationservice.service;

import com.chenluo.laborchestrationservice.config.LabLaunchMessagingConfig;
import com.chenluo.laborchestrationservice.model.LabLaunchCommand;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LabLaunchCommandListener {

    private final LabManagerService labManagerService;

    public LabLaunchCommandListener(LabManagerService labManagerService) {
        this.labManagerService = labManagerService;
    }

    @RabbitListener(queues = LabLaunchMessagingConfig.LAB_LAUNCH_QUEUE)
    public void handleLaunchCommand(LabLaunchCommand command) {
        labManagerService.processLaunchCommand(command);
    }
}
