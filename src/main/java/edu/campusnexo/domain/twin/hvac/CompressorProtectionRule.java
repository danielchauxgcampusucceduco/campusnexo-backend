package edu.campusnexo.domain.twin.hvac;

import edu.campusnexo.domain.climate.CompressorGuard;
import edu.campusnexo.domain.twin.HvacSetting;

/**
 * Asks the rest of the chain and delays the change when it would short-cycle the compressor: the unit keeps
 * its current setting until the family's minimum on or off time has passed.
 */
final class CompressorProtectionRule implements HvacSettingRule {
    @Override
    public HvacResolution resolve(HvacRequest request, HvacSettingChain next) {
        HvacResolution proposal = next.proceed(request);
        if (request.firstDecision()) {
            return proposal;
        }
        HvacSetting current = request.previous();
        CompressorGuard guard = request.family().compressorGuard();
        long minutes = current.minutesSince(request.now());
        if (!guard.mustHold(current.compressorOn(), proposal.compressorOn(), minutes)) {
            return proposal;
        }
        return new HvacResolution(current.state(), current.mode(), current.setpointC(),
                "Se mantiene " + current.state() + " por protección del compresor (mínimo "
                        + guard.minimumFor(current.compressorOn()) + " min, lleva " + minutes + "). Pendiente: "
                        + proposal.explanation(), ruleName());
    }
}
