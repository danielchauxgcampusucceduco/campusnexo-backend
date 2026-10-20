package edu.campusnexo.application.port.in;

import java.time.Instant;

public interface DashboardQueryUseCase {
    DashboardSummary summarizeLast24Hours();

    record DashboardSummary(long monitoredSpaces, long readingsLast24h, Instant since) {}
}
