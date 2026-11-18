package edu.campusnexo.application.port.in;

/** The camera count and the Wi-Fi devices of a room; both are optional. */
public record EstimateOccupancyCommand(
        String spaceId,
        Integer cameraPeople,
        Integer wifiDevices) {
}
