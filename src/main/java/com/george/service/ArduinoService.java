package com.george.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.george.exception.ArduinoServiceException;
import com.george.model.IrrigationStatus;
import com.george.model.LandStatus;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class ArduinoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArduinoService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUEUE_NAME = "sensors-queue";

    private String commandQueueName;

    private BufferedReader bufferedReader;

    private BufferedWriter bufferedWriter;

    private Channel channel;

    private Set<String> registeredExchanges = new HashSet<>();

    public ArduinoService(String port, String rabbitMQHost) throws IOException, TimeoutException, InterruptedException {
        SerialPort serialPort = SerialPort.getCommPort(port);
        LOGGER.info("connecting to: {}", serialPort);
        serialPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        serialPort.openPort();
        LOGGER.info("connected to: {}", serialPort);
        Thread.sleep(2000);
        InputStreamReader inputStreamReader = new InputStreamReader(serialPort.getInputStream());
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(serialPort.getOutputStream());
        bufferedReader = new BufferedReader(inputStreamReader);
        bufferedWriter = new BufferedWriter((outputStreamWriter));

        LOGGER.info("host ip: {}", InetAddress.getLocalHost());

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitMQHost);
        Connection connection = connectionFactory.newConnection();
        channel = connection.createChannel();

        LOGGER.info("declaring queue {}", QUEUE_NAME);
        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        LOGGER.info("declared queue {}", declareOk);

        declareOk = channel.queueDeclare();
        LOGGER.info("declared command queue {}", declareOk);
        commandQueueName = declareOk.getQueue();

        channel.basicConsume(commandQueueName, true, (consumerTag, delivery) -> {

            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            LOGGER.info("consumerTag: {}", consumerTag);
            LOGGER.info("message: {}", message);
            IrrigationStatus irrigationStatus = OBJECT_MAPPER.readValue(message, IrrigationStatus.class);
            try {
                setIrrigationStatus(irrigationStatus);
            } catch (ArduinoServiceException e) {
                e.printStackTrace();
            }

        }, consumerTag -> { LOGGER.info("consumer shutdown"); });

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void setIrrigationStatus(IrrigationStatus irrigationStatus) throws ArduinoServiceException {

        try {
            bufferedWriter.write(irrigationStatus.getSymbol());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new ArduinoServiceException(e);
        }

    }

    public void run(String... args) throws Exception {
        String input;
        while (true) {
            if (bufferedReader.ready()) {
                input = bufferedReader.readLine();
                LOGGER.info("{}", input);
                String[] tokens = input.split(",");
                if (tokens.length == 3) {

                    String place = tokens[0].trim();
                    String moisture = tokens[1].trim();
                    String irrigation = tokens[2].trim();

                    if (!registeredExchanges.contains(place)) {
                        AMQP.Exchange.DeclareOk declareOk = channel.exchangeDeclare(place, "fanout");
                        LOGGER.info("declared exchange {}", declareOk);

                        AMQP.Queue.BindOk bindOk = channel.queueBind(commandQueueName, place, "");
                        LOGGER.info("declared binding {}", bindOk);
                        registeredExchanges.add(place);
                    }

                    try {
                        Double moistureNumber = Double.parseDouble(moisture);
                        LOGGER.info("sensor input: {}", moistureNumber);
                        LandStatus landStatus = new LandStatus();
                        landStatus.setPlace(place);
                        landStatus.setMoisture(moistureNumber);
                        if (irrigation.equals("1")) {
                            landStatus.setIrrigationStatus(IrrigationStatus.ON);

                        } else if (irrigation.equals("0")) {
                            landStatus.setIrrigationStatus(IrrigationStatus.OFF);
                        }

                        channel.basicPublish("", QUEUE_NAME, null, OBJECT_MAPPER.writeValueAsBytes(landStatus));

                    } catch (NumberFormatException e) {
                        LOGGER.warn("exception: {}", e.getMessage());
                        e.printStackTrace();
                    }

                }
            } else {
                Thread.sleep(10);
            }
        }
    }

}
