package com.george.web;

import com.george.exception.ArduinoServiceException;
import com.george.model.IrrigationStatus;
import com.george.service.ArduinoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/arduino", produces = "application/json")
public class ArduinoEndpoint {

    @Autowired
    private ArduinoService arduinoService;

    @GetMapping("/status")
    public IrrigationStatus getIrrigationStatus() throws ArduinoServiceException {
        return arduinoService.getIrrigationStatus();
    }

    @PostMapping("/status")
    public IrrigationStatus setIrrigationStatus(@RequestBody IrrigationStatus irrigationStatus) throws ArduinoServiceException {
        return arduinoService.setIrrigationStatus(irrigationStatus);
    }

}
