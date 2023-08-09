package com.george.service;

import com.george.model.Status;

public interface IrrigationCommandReceiver {

    void receiveCommand(String place, Status irrigationStatus);

}
