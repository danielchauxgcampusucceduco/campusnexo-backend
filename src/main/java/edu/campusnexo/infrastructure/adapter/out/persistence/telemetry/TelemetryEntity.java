package edu.campusnexo.infrastructure.adapter.out.persistence.telemetry;

import edu.campusnexo.domain.telemetry.Telemetry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "telemetry_stream")
class TelemetryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "people_count", nullable = false)
    private int peopleCount;

    @Column(name = "object_count", nullable = false)
    private int objectCount;

    @Column(name = "wifi_devices", nullable = false)
    private int wifiDevices;

    @Column(name = "co2_ppm", nullable = false)
    private double co2Ppm;

    @Column(name = "temperature_c", nullable = false)
    private double temperatureC;

    @Column(name = "humidity_pct", nullable = false)
    private double humidityPct;

    @Column(name = "illuminance_lux", nullable = false)
    private double illuminanceLux;

    @Column(name = "active_power_kw", nullable = false)
    private double activePowerKw;

    /** The camera's own count in that frame; {@code null} when no camera saw the room. */
    @Column(name = "camera_people")
    private Integer cameraPeople;

    protected TelemetryEntity() {
    }

    TelemetryEntity(Telemetry telemetry) {
        spaceId = telemetry.spaceId();
        recordedAt = telemetry.recordedAt();
        peopleCount = telemetry.peopleCount();
        objectCount = telemetry.objectCount();
        wifiDevices = telemetry.wifiDevices();
        co2Ppm = telemetry.co2Ppm();
        temperatureC = telemetry.temperatureC();
        humidityPct = telemetry.humidityPct();
        illuminanceLux = telemetry.illuminanceLux();
        activePowerKw = telemetry.activePowerKw();
        cameraPeople = telemetry.cameraPeople();
    }

    Telemetry toDomain() {
        return new Telemetry(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, co2Ppm, temperatureC,
                humidityPct, illuminanceLux, activePowerKw, cameraPeople);
    }
}
