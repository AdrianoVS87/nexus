package com.nexus.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.mapping.Jackson2JavaTypeMapper;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name("payments").partitions(3).replicas(1).build();
    }

    @Bean
    public RecordMessageConverter messageConverter() {
        var converter = new JsonMessageConverter();
        var typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
        typeMapper.addTrustedPackages("com.nexus.*");
        typeMapper.setIdClassMapping(Map.of(
                "PaymentRequested", com.nexus.payment.domain.event.PaymentRequested.class,
                "PaymentCompleted", com.nexus.payment.domain.event.PaymentCompleted.class,
                "PaymentFailed", com.nexus.payment.domain.event.PaymentFailed.class,
                "PaymentRefundRequested", com.nexus.payment.domain.event.PaymentRefundRequested.class
        ));
        converter.setTypeMapper(typeMapper);
        return converter;
    }
}
