package edu.campusnexo.infrastructure.persistence;

import edu.campusnexo.domain.model.Telemetry;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "telemetry_stream", indexes = @Index(name = "idx_telemetry_space_time", columnList = "space_id, recorded_at"))
public class TelemetryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="space_id", nullable=false, length=32) private String spaceId;
    @Column(name="recorded_at", nullable=false) private Instant recordedAt;
    @Column(name="people_count", nullable=false) private int peopleCount;
    @Column(name="object_count", nullable=false) private int objectCount;
    @Column(name="wifi_devices", nullable=false) private int wifiDevices;
    @Column(name="co2_ppm", nullable=false) private double co2Ppm;
    @Column(name="temperature_c", nullable=false) private double temperatureC;
    @Column(name="humidity_pct", nullable=false) private double humidityPct;
    @Column(name="illuminance_lux", nullable=false) private double illuminanceLux;
    @Column(name="active_power_kw", nullable=false) private double activePowerKw;
    protected TelemetryEntity() {}
    public TelemetryEntity(Telemetry t) {
        spaceId=t.spaceId(); recordedAt=t.recordedAt(); peopleCount=t.peopleCount(); objectCount=t.objectCount();
        wifiDevices=t.wifiDevices(); co2Ppm=t.co2Ppm(); temperatureC=t.temperatureC(); humidityPct=t.humidityPct();
        illuminanceLux=t.illuminanceLux(); activePowerKw=t.activePowerKw();
    }
    public Telemetry toDomain() { return new Telemetry(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, co2Ppm, temperatureC, humidityPct, illuminanceLux, activePowerKw); }
}
