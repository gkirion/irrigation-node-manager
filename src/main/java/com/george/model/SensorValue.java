package com.george.model;

import java.util.Objects;

public class SensorValue {

    private String place;
    private Double value;

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensorValue that = (SensorValue) o;
        return Objects.equals(place, that.place) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(place, value);
    }

    @Override
    public String toString() {
        return "SensorValue{" +
                "place='" + place + '\'' +
                ", value=" + value +
                '}';
    }

}
