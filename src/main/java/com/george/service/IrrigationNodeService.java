package com.george.service;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class IrrigationNodeService implements CommandLineRunner  {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrrigationNodeService.class);

    @Value("${serial.maxNumberOfAttempts:3}")
    private int maxNumberOfAttempts;

    @Autowired
    private IrrigationQueue irrigationQueue;

    @Override
    public void run(String... args) throws Exception {

        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(SerialPort.getCommPorts().length);
        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            LOGGER.info("serialPort: {} {}", serialPort.getSystemPortName(), serialPort);

                threadPoolExecutor.submit(() -> {
                    try {
                        new IrrigationNodeSerial(serialPort.getSystemPortName(), maxNumberOfAttempts, irrigationQueue);

                    } catch (Exception e) {
                        LOGGER.info("could not create arduino service for: {} {}", serialPort.getSystemPortName(), serialPort, e);
                    }
                });

        }

    }

}
