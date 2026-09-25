package dev.codepulse.engine;

import java.util.Objects;

/**
 * A counted observation shown next to the score without changing it, e.g. TODO markers
 * (API {@code additionalSignals}). Blueprint 10.2: marker labels never raise priority by themselves.
 */
public record AdditionalSignal(String code, int count) {

    public AdditionalSignal {
        Objects.requireNonNull(code);
        if (count < 1) {
            throw new IllegalArgumentException("a signal is only reported when its count is >= 1");
        }
    }
}
