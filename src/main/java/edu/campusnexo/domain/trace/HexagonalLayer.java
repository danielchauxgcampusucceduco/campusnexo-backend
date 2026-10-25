package edu.campusnexo.domain.trace;

/** Ring of the hexagonal architecture a trace step belongs to, from the outside in and back out. */
public enum HexagonalLayer {
    INBOUND_ADAPTER,
    INPUT_PORT,
    USE_CASE,
    DOMAIN_SERVICE,
    OUTPUT_PORT,
    OUTBOUND_ADAPTER
}
