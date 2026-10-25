package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import java.util.List;

public interface TwinQueryUseCase {
    List<SpaceTwinView> spaces();

    SpaceTwinView space(String spaceId);

    SavingsReport savings();

    List<ArchitectureTrace> recentTraces(int limit);
}
