package ru.alex.braim.exception;

public class IncompatibleData extends RuntimeException{
    public IncompatibleData() {
    }

    public IncompatibleData(String message) {
        super(message);
    }
}
