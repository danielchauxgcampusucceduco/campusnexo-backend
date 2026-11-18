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

    @Column(name = "wifi_devices")
    private Integer wifiDevices;






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
        cameraPeople = telemetry.cameraPeople();
    }

    Telemetry toDomain() {
        return new Telemetry(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, cameraPeople);
    }
}
