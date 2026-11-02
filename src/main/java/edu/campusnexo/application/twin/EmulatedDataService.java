package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.in.EmulatedDataUseCase;
import edu.campusnexo.application.port.in.TwinControlUseCase;
import edu.campusnexo.application.port.out.EmulatedDataPort;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.application.port.out.UnitOfWork;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.List;

/**
 * A room is emulated when its last camera frame came from a test source (the demo or the device emulator). Its rows
 * go in one transaction, its twin starts again (as if it had never reported, so the screens see it at once) and its
 * emulated history leaves MongoDB. Rooms with a real camera are not touched.
 */
public class EmulatedDataService implements EmulatedDataUseCase {
    private final SpaceRepositoryPort twins;
    private final EmulatedDataPort store;
    private final TwinHistoryPort history;
    private final TwinControlUseCase controls;
    private final UnitOfWork unitOfWork;

    public EmulatedDataService(SpaceRepositoryPort twins, EmulatedDataPort store, TwinHistoryPort history,
                               TwinControlUseCase controls, UnitOfWork unitOfWork) {
        this.twins = twins;
        this.store = store;
        this.history = history;
        this.controls = controls;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public List<String> purge() {
        List<String> rooms = twins.findAllTwins().stream()
                .filter(twin -> twin.sensing().emulated())
                .map(SpaceTwinState::spaceId)
                .toList();
        for (String spaceId : rooms) {
            unitOfWork.inTransaction(() -> {
                store.forget(spaceId);
                return null;
            });
            controls.reset(spaceId);
            history.forgetEmulated(spaceId);
        }
        return rooms;
    }
}
