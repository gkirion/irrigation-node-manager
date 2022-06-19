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
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

@Service
public class ArduinoServiceImpl implements ArduinoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArduinoServiceImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUEUE_NAME = "sensors-queue";

    private static final Object SYNCHRONIZATION_OBJECT = new Object();

    private Optional<IrrigationStatus> irrigationStatus = Optional.empty();

    private BufferedReader bufferedReader;

    private BufferedWriter bufferedWriter;

    private Channel channel;

    public ArduinoServiceImpl() throws IOException, TimeoutException {
        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            LOGGER.info("serialPort: {} {}", serialPort.getSystemPortName(), serialPort);
        }
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

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public IrrigationStatus getIrrigationStatus() throws ArduinoServiceException {

        synchronized (SYNCHRONIZATION_OBJECT) {
            while (irrigationStatus.isEmpty()) {
                try {
                    SYNCHRONIZATION_OBJECT.wait();
                } catch (InterruptedException e) {
                    throw new ArduinoServiceException(e);
                }
            }
            return irrigationStatus.get();
        }

    }

    @Override
    public IrrigationStatus setIrrigationStatus(IrrigationStatus irrigationStatus) throws ArduinoServiceException {

        IrrigationStatus previousIrrigationStatus = getIrrigationStatus();
        try {
            bufferedWriter.write(irrigationStatus.getSymbol());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new ArduinoServiceException(e);
        }
        return previousIrrigationStatus;

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
                        synchronized (SYNCHRONIZATION_OBJECT) {
                            irrigationStatus = Optional.of(landStatus.getIrrigationStatus());
                            SYNCHRONIZATION_OBJECT.notify();
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
