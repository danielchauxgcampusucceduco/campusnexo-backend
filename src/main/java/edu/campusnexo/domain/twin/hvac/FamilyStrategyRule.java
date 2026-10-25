package edu.campusnexo.domain.twin.hvac;

import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.twin.HvacControlState;

/**
 * The climate family decides <em>what</em> the unit does while people are in the room. When the predictive
 * controller runs the unit, the family's strategy chooses the mode and setpoint (heating first in the
 * Andean family, for example); when the room is thermally fine but too humid, the tropical strategy turns on
 * dehumidification, which the thermal model alone does not see.
 */
final class FamilyStrategyRule implements HvacSettingRule {
    @Override
    public HvacResolution resolve(HvacRequest request, HvacSettingChain next) {
        HvacResolution proposal = next.proceed(request);
        if (!request.climate().occupied()) {
            return proposal;
        }
        HvacDecision family = request.family().createThermalStrategy().recommend(request.climate());
        String profile = request.family().climateProfile().name();
        if (proposal.compressorOn() && family.mode().isActive()) {
            return new HvacResolution(proposal.state(), family.mode(), family.setpointC(),
                    proposal.explanation() + "; modo de la familia " + profile + ": " + family.explanation(), ruleName());
        }
        if (proposal.state() == HvacControlState.CORTE_INERCIA_TERMICA && family.mode() == HvacMode.DESHUMIDIFICACION) {
            return new HvacResolution(HvacControlState.CONFORT_ACTIVO, HvacMode.DESHUMIDIFICACION, family.setpointC(),
                    "Familia " + profile + ": " + family.explanation(), ruleName());
        }
        return proposal;
    }
}
