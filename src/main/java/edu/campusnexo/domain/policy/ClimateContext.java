package edu.campusnexo.domain.policy;

public record ClimateContext(
        int occupancy,
        double co2Ppm,
        double indoorTemperatureC,
        double outsideTemperatureC,
        double illuminanceLux,
        boolean hvacOn,
        int minutesInCurrentHvacState) {
    public ClimateContext {
        if (occupancy < 0 || minutesInCurrentHvacState < 0) throw new IllegalArgumentException("Conteos y duración HVAC no pueden ser negativos");
        if (!Double.isFinite(co2Ppm) || co2Ppm < 0 || !Double.isFinite(indoorTemperatureC)
                || !Double.isFinite(outsideTemperatureC) || !Double.isFinite(illuminanceLux) || illuminanceLux < 0) {
            throw new IllegalArgumentException("Contexto climático no válido");
        }
    }
}
