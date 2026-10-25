package edu.campusnexo.application.port.in;

/** {@code cameraPeople} and {@code outdoorCo2Ppm} are optional. */
public record EstimateOccupancyCommand(
        String spaceId,
        Integer cameraPeople,
        double co2Ppm,
        int wifiDevices,
        Double outdoorCo2Ppm) {
}
