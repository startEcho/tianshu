package com.chenluo.laborchestrationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LabLaunchMessagingConfig {

    public static final String LAB_COMMAND_EXCHANGE = "lab.command.exchange";
    public static final String LAB_LAUNCH_QUEUE = "lab.launch.queue";
    public static final String LAB_LAUNCH_ROUTING_KEY = "lab.launch.requested";

    @Bean
    public DirectExchange labCommandExchange() {
        return new DirectExchange(LAB_COMMAND_EXCHANGE, true, false);
    }

    @Bean
    public Queue labLaunchQueue() {
        return new Queue(LAB_LAUNCH_QUEUE, true);
    }

    @Bean
    public Binding labLaunchBinding(Queue labLaunchQueue, DirectExchange labCommandExchange) {
        return BindingBuilder.bind(labLaunchQueue).to(labCommandExchange).with(LAB_LAUNCH_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
