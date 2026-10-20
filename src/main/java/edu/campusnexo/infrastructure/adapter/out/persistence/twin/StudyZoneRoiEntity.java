package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;

/** Desk region; {@code polygon} is stored as {@code "x1 y1;x2 y2;..."} in normalized coordinates. */
@Entity
@Table(name = "study_zone_rois")
class StudyZoneRoiEntity {
    @Id
    @Column(name = "zone_id", length = 32)
    private String zoneId;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "polygon", nullable = false, columnDefinition = "TEXT")
    private String polygon;

    protected StudyZoneRoiEntity() {
    }

    StudyZoneRoi toDomain() {
        return new StudyZoneRoi(zoneId, spaceId, label, parse(polygon));
    }

    static List<NormalizedPoint> parse(String encoded) {
        return Arrays.stream(encoded.trim().split(";"))
                .map(String::trim)
                .filter(vertex -> !vertex.isEmpty())
                .map(vertex -> {
                    String[] parts = vertex.split("\\s+");
                    if (parts.length != 2) {
                        throw new IllegalStateException("Vértice de ROI mal formado: " + vertex);
                    }
                    return new NormalizedPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                })
                .toList();
    }

}
