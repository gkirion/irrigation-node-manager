package com.george.service;

import com.fazecast.jSerialComm.SerialPort;
import com.george.exception.IrrigationNodeException;
import com.george.model.LandStatus;
import com.george.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IrrigationNodeSerial implements IrrigationCommandReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrrigationNodeSerial.class);

    private BufferedReader bufferedReader;

    private BufferedWriter bufferedWriter;

    private Set<String> registeredRoutingKeys = new HashSet<>();

    private IrrigationQueue irrigationQueue;

    public IrrigationNodeSerial(String port, int maxNumberOfAttempts, IrrigationQueue irrigationQueue) {
        SerialPort serialPort = SerialPort.getCommPort(port);
        LOGGER.info("connecting to: {}", serialPort);
        serialPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 200, 0);
        boolean connected;
        int attemptNumber = 0;

        do {
            attemptNumber++;
            LOGGER.info("connection attempt: {}, max number of attempts: {}", attemptNumber, maxNumberOfAttempts);
            connected = serialPort.openPort();
        } while (!connected && attemptNumber < maxNumberOfAttempts);

        if (!connected) {
            throw new RuntimeException("could not connect to " + serialPort);
        }
        LOGGER.info("connected to: {}", serialPort);

        InputStreamReader inputStreamReader = new InputStreamReader(serialPort.getInputStream());
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(serialPort.getOutputStream());
        bufferedReader = new BufferedReader(inputStreamReader);
        bufferedWriter = new BufferedWriter((outputStreamWriter));

        this.irrigationQueue = irrigationQueue;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                readSerial();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    private void setIrrigationStatus(String place, Status irrigationStatus) throws IrrigationNodeException {

        try {
            bufferedWriter.write(place + "," + irrigationStatus.getSymbol() + System.lineSeparator());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new IrrigationNodeException(e);
        }

    }

    private void readSerial() throws Exception {
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

                    if (!registeredRoutingKeys.contains(place)) {
                        irrigationQueue.addIrrigationCommandReceiver(place, this);
                        registeredRoutingKeys.add(place);
                    }

                    try {
                        Double moistureNumber = Double.parseDouble(moisture);
                        LOGGER.info("sensor input: {}", moistureNumber);
                        LandStatus landStatus = new LandStatus();
                        landStatus.setPlace(place);
                        landStatus.setMoisture(moistureNumber);
                        if (irrigation.equals("1")) {
                            landStatus.setIrrigationStatus(Status.ON);

                        } else if (irrigation.equals("0")) {
                            landStatus.setIrrigationStatus(Status.OFF);
                        }

                        irrigationQueue.sendSensorReading(landStatus);

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

    @Override
    public void receiveCommand(String place, Status irrigationStatus) {
        try {
            setIrrigationStatus(place, irrigationStatus);
        } catch (IrrigationNodeException e) {
            e.printStackTrace();
        }
    }

}
