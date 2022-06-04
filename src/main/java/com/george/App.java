package com.george;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.george.model.SensorValue;
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.concurrent.TimeoutException;

@SpringBootApplication
public class App implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUEUE_NAME = "sensors-queue";

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        SerialPort serialPort = SerialPort.getCommPort("COM5");
        LOGGER.info("{}",serialPort);
        serialPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        serialPort.openPort();

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        try (InputStreamReader inputStreamReader = new InputStreamReader(serialPort.getInputStream());
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(serialPort.getOutputStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
             BufferedWriter bufferedWriter = new BufferedWriter((outputStreamWriter));
             Connection connection = connectionFactory.newConnection();
             Channel channel = connection.createChannel()) {

            DeclareOk declareOk = channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            LOGGER.info("declared queue {}", declareOk);
            String input;
            while (true) {
                if (bufferedReader.ready()) {
                    input = bufferedReader.readLine();
                    LOGGER.info("{}", input);
                    String[] tokens = input.split(":");
                    if (tokens.length == 2) {

                        String[] sensorInputs = tokens[1].trim().split(" ");
                        for (String sensorInput : sensorInputs) {
                            try {
                                Double sensorInputNumber = Double.parseDouble(sensorInput);
                                LOGGER.info("sensor input: {}", sensorInputNumber);
                                SensorValue sensorValue = new SensorValue();
                                sensorValue.setPlace("glastraki");
                                sensorValue.setValue(sensorInputNumber);
                                channel.basicPublish("", QUEUE_NAME, null, OBJECT_MAPPER.writeValueAsBytes(sensorValue));

                            } catch (NumberFormatException e) {
                                LOGGER.warn("exception: {}", e.getMessage());
                                e.printStackTrace();
                            }
                        }

                    }
                } else {
                    Thread.sleep(10);
                }
            }

        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

}
