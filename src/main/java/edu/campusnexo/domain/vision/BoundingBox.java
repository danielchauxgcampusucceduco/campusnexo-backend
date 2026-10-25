package edu.campusnexo.domain.vision;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;

/** Axis-aligned box in normalized image coordinates (YOLO "xyxyn" format). */
public record BoundingBox(double xMin, double yMin, double xMax, double yMax) {
    public BoundingBox {
        requireRange(xMin, 0, 1, "bbox.xMin");
        requireRange(yMin, 0, 1, "bbox.yMin");
        requireRange(xMax, 0, 1, "bbox.xMax");
        requireRange(yMax, 0, 1, "bbox.yMax");
        require(xMax > xMin && yMax > yMin, "bbox debe tener ancho y alto positivos");
    }

    public NormalizedPoint center() {
        return new NormalizedPoint((xMin + xMax) / 2, (yMin + yMax) / 2);
    }
}
