// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.config;

/** Player targeting scope. Serialized by Gson as name strings. */
public enum PlayerMode {
    OFF,
    ALL,
    BY_NAME;

    public PlayerMode next() {
        if (this == OFF) {
            return ALL;
        }
        if (this == ALL) {
            return BY_NAME;
        }
        return OFF;
    }
}
