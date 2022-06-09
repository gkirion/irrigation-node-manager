package com.george.model;

public enum IrrigationStatus {

    ON('1'), OFF('0');

    private char symbol;

    IrrigationStatus(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }

}
