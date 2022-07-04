package com.george.service;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

@Service
public class IrrigationNodeService implements CommandLineRunner  {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrrigationNodeService.class);

    @Value("${rabbitmq-host:localhost}")
    private String rabbitMQHost;

    @Override
    public void run(String... args) throws Exception {

        for (SerialPort serialPort : SerialPort.getCommPorts()) {
            LOGGER.info("serialPort: {} {}", serialPort.getSystemPortName(), serialPort);
        }
        ArduinoService arduinoService = new ArduinoService("COM5", rabbitMQHost);

    }

}
