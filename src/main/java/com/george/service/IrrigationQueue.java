package com.george.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.george.model.LandStatus;
import com.george.model.Status;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class IrrigationQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrrigationQueue.class);

    @Value("${rabbitmq-host:localhost}")
    private String rabbitMQHost;

    private static final String QUEUE_NAME = "sensors-queue";

    private static final String EXCHANGE_NAME = "commands-exchange";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Channel channel;

    private Map<String, IrrigationCommandReceiver> irrigationCommandReceivers = new HashMap<>();

    @PostConstruct
    private void init() throws IOException, TimeoutException {
        LOGGER.info("host ip: {}", InetAddress.getLocalHost());

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitMQHost);
        Connection connection = connectionFactory.newConnection();
        channel = connection.createChannel();

        LOGGER.info("declaring queue {}", QUEUE_NAME);
        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        LOGGER.info("declared queue {}", declareOk);

        LOGGER.info("declaring exchange {}", EXCHANGE_NAME);
        AMQP.Exchange.DeclareOk exchangeDeclareOk = channel.exchangeDeclare(EXCHANGE_NAME, "direct");
        LOGGER.info("declared exchange {}", exchangeDeclareOk);

    }

    public void addIrrigationCommandReceiver(String place, IrrigationCommandReceiver irrigationCommandReceiver) throws IOException {
        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare();
        LOGGER.info("declared command queue {}", declareOk);
        String commandQueueName = declareOk.getQueue();

        channel.basicConsume(commandQueueName, true, (consumerTag, delivery) -> {

            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String routingKey = delivery.getEnvelope().getRoutingKey();
            LOGGER.info("consumerTag: {}", consumerTag);
            LOGGER.info("message: {}", message);
            LOGGER.info("routing key: {}", routingKey);
            Status irrigationStatus = OBJECT_MAPPER.readValue(message, Status.class);
            if (irrigationCommandReceivers.containsKey(routingKey)) {
                irrigationCommandReceivers.get(routingKey).receiveCommand(routingKey, irrigationStatus);
            }

        }, consumerTag -> { LOGGER.info("consumer shutdown"); });

        AMQP.Queue.BindOk bindOk = channel.queueBind(commandQueueName, EXCHANGE_NAME, place);
        LOGGER.info("declared binding {}", bindOk);
        irrigationCommandReceivers.put(place, irrigationCommandReceiver);
    }

    public void sendSensorReading(LandStatus landStatus) throws IOException {
        channel.basicPublish("", QUEUE_NAME, null, OBJECT_MAPPER.writeValueAsBytes(landStatus));
    }


}
