package edu.campusnexo.domain.twin.hvac;

/**
 * Chain of Responsibility handler: decides the HVAC setting of a room, or passes the request on with
 * {@code next.proceed(request)} (and may then adjust what the rest of the chain proposed).
 */
public interface HvacSettingRule {
    HvacResolution resolve(HvacRequest request, HvacSettingChain next);

    default String ruleName() {
        return getClass().getSimpleName();
    }
}
