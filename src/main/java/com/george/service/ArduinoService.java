package com.george.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.george.model.IrrigationStatus;
import com.george.model.SensorValue;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Service
public class ArduinoService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArduinoService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUEUE_NAME = "sensors-queue";

    private static final Object SYNCHRONIZATION_OBJECT = new Object();

    private Optional<IrrigationStatus> irrigationStatus = Optional.empty();

    private BufferedReader bufferedReader;

    private BufferedWriter bufferedWriter;

    private Channel channel;

    public ArduinoService() throws IOException, TimeoutException {
        SerialPort serialPort = SerialPort.getCommPort("COM5");
        LOGGER.info("connecting to: {}", serialPort);
        serialPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        serialPort.openPort();
        LOGGER.info("connected to: {}", serialPort);
        InputStreamReader inputStreamReader = new InputStreamReader(serialPort.getInputStream());
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(serialPort.getOutputStream());
        bufferedReader = new BufferedReader(inputStreamReader);
        bufferedWriter = new BufferedWriter((outputStreamWriter));

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        Connection connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        LOGGER.info("declaring queue {}", QUEUE_NAME);
        AMQP.Queue.DeclareOk declareOk = channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        LOGGER.info("declared queue {}", declareOk);
    }

    public IrrigationStatus getIrrigationStatus() throws IOException, InterruptedException {
        bufferedWriter.write('S');
        bufferedWriter.flush();
        IrrigationStatus currentIrrigationStatus;
        synchronized (SYNCHRONIZATION_OBJECT) {
            while (irrigationStatus.isEmpty()) {
                SYNCHRONIZATION_OBJECT.wait();
            }
            currentIrrigationStatus = irrigationStatus.get();
            irrigationStatus = Optional.empty();
        }
        return currentIrrigationStatus;
    }

    public IrrigationStatus setIrrigationStatus(IrrigationStatus irrigationStatus) throws IOException, InterruptedException {
        bufferedWriter.write(irrigationStatus.getSymbol());
        bufferedWriter.flush();
        return getIrrigationStatus();
    }

    @Override
    public void run(String... args) throws Exception {
        String input;
        while (true) {
            if (bufferedReader.ready()) {
                input = bufferedReader.readLine();
                LOGGER.info("{}", input);
                String[] tokens = input.split(",");
                if (tokens.length == 2) {

                    String place = tokens[0].trim();
                    tokens = tokens[1].split(":");
                    if (tokens.length == 2) {

                        if (tokens[0].trim().equals("irrigation")) {
                            synchronized (SYNCHRONIZATION_OBJECT) {
                                if (tokens[1].trim().equals("1")) {
                                    irrigationStatus = Optional.of(IrrigationStatus.ON);
                                    SYNCHRONIZATION_OBJECT.notify();

                                } else if (tokens[1].trim().equals("0")) {
                                    irrigationStatus = Optional.of(IrrigationStatus.OFF);
                                    SYNCHRONIZATION_OBJECT.notify();
                                }
                            }
                        } else if (tokens[0].trim().equals("moisture")) {
                            String sensorInput = tokens[1].trim();
                            try {
                                Double sensorInputNumber = Double.parseDouble(sensorInput);
                                LOGGER.info("sensor input: {}", sensorInputNumber);
                                SensorValue sensorValue = new SensorValue();
                                sensorValue.setPlace(place);
                                sensorValue.setValue(sensorInputNumber);
                                channel.basicPublish("", QUEUE_NAME, null, OBJECT_MAPPER.writeValueAsBytes(sensorValue));

                            } catch (NumberFormatException e) {
                                LOGGER.warn("exception: {}", e.getMessage());
                                e.printStackTrace();
                            }
                        }

                    }

                }
            } else {
                Thread.sleep(10);
            }
        }
    }

}
