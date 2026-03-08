package com.vnpay.platform.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange names
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String CALLBACK_EXCHANGE = "callback.exchange";
    public static final String DEVICE_EXCHANGE = "device.exchange";

    // Queue names
    public static final String PAYMENT_CREATED_QUEUE = "payment.created.queue";
    public static final String PAYMENT_STATUS_QUEUE = "payment.status.queue";
    public static final String CALLBACK_QUEUE = "callback.queue";
    public static final String CALLBACK_RETRY_QUEUE = "callback.retry.queue";
    public static final String DEVICE_NOTIFICATION_QUEUE = "device.notification.queue";

    // Routing keys
    public static final String PAYMENT_CREATED_KEY = "payment.created";
    public static final String PAYMENT_STATUS_KEY = "payment.status";
    public static final String CALLBACK_KEY = "callback.send";
    public static final String CALLBACK_RETRY_KEY = "callback.retry";
    public static final String DEVICE_NOTIFICATION_KEY = "device.notification";

    // Exchanges
    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange callbackExchange() {
        return new DirectExchange(CALLBACK_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deviceExchange() {
        return new DirectExchange(DEVICE_EXCHANGE, true, false);
    }

    // Queues
    @Bean
    public Queue paymentCreatedQueue() {
        return QueueBuilder.durable(PAYMENT_CREATED_QUEUE).build();
    }

    @Bean
    public Queue paymentStatusQueue() {
        return QueueBuilder.durable(PAYMENT_STATUS_QUEUE).build();
    }

    @Bean
    public Queue callbackQueue() {
        return QueueBuilder.durable(CALLBACK_QUEUE).build();
    }

    @Bean
    public Queue callbackRetryQueue() {
        return QueueBuilder.durable(CALLBACK_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", CALLBACK_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CALLBACK_KEY)
                .build();
    }

    @Bean
    public Queue deviceNotificationQueue() {
        return QueueBuilder.durable(DEVICE_NOTIFICATION_QUEUE).build();
    }

    // Bindings
    @Bean
    public Binding paymentCreatedBinding() {
        return BindingBuilder.bind(paymentCreatedQueue())
                .to(paymentExchange()).with(PAYMENT_CREATED_KEY);
    }

    @Bean
    public Binding paymentStatusBinding() {
        return BindingBuilder.bind(paymentStatusQueue())
                .to(paymentExchange()).with(PAYMENT_STATUS_KEY);
    }

    @Bean
    public Binding callbackBinding() {
        return BindingBuilder.bind(callbackQueue())
                .to(callbackExchange()).with(CALLBACK_KEY);
    }

    @Bean
    public Binding callbackRetryBinding() {
        return BindingBuilder.bind(callbackRetryQueue())
                .to(callbackExchange()).with(CALLBACK_RETRY_KEY);
    }

    @Bean
    public Binding deviceNotificationBinding() {
        return BindingBuilder.bind(deviceNotificationQueue())
                .to(deviceExchange()).with(DEVICE_NOTIFICATION_KEY);
    }

    // Message converter
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter());
        factory.setPrefetchCount(10);
        return factory;
    }
}
