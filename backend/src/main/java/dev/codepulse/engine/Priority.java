package dev.codepulse.engine;

/**
 * Review priority of one file (schema {@code priority}, API {@code Priority}).
 *
 * <p>UNASSESSED is not "lowest". It means no score exists because the file did not parse. The
 * other four are the {@code structural-v1} bands: LOW 0-24, MODERATE 25-49, HIGH 50-74,
 * VERY_HIGH 75-100 (Blueprint 10.2). Declaration order is the ranking order.
 */
public enum Priority {
    UNASSESSED,
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH;

    /** @return the higher of the two rankings, used by minimum-priority overrides */
    public Priority atLeast(Priority floor) {
        return this.ordinal() >= floor.ordinal() ? this : floor;
    }
}
