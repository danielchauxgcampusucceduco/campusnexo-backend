package edu.campusnexo.domain.telemetry;

/** Physical ranges accepted by the system. Every layer reuses these values instead of redefining them. */
public final class MeasurementLimits {
    public static final int MAX_PEOPLE = 5_000;
    public static final int MAX_DEVICES = 10_000;
    public static final double MIN_CO2_PPM = 0;
    public static final double MAX_CO2_PPM = 10_000;
    public static final double MIN_TEMPERATURE_C = -30;
    public static final double MAX_TEMPERATURE_C = 60;
    public static final double MIN_HUMIDITY_PCT = 0;
    public static final double MAX_HUMIDITY_PCT = 100;
    public static final double MIN_ILLUMINANCE_LUX = 0;
    public static final double MAX_ILLUMINANCE_LUX = 20_000;
    public static final double MIN_POWER_KW = 0;
    public static final double MAX_POWER_KW = 10_000;
    public static final double MIN_OUTDOOR_CO2_PPM = 300;
    public static final double MAX_OUTDOOR_CO2_PPM = 1_000;

    private MeasurementLimits() {
    }
}
