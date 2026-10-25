package com.scmarket.rtp;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtpBotSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsConfiguredDelayAcrossRestart() throws Exception {
        Path file = temporaryDirectory.resolve("config/sc-market/rtp-bot.properties");
        RtpBotSettingsStore first = new RtpBotSettingsStore(file);
        first.saveDelaySeconds(120);

        assertEquals(120, new RtpBotSettingsStore(file).loadDelaySeconds());
    }

    @Test
    void fallsBackToSafeDefaultForInvalidStoredDelay() throws Exception {
        Path file = temporaryDirectory.resolve("rtp-bot.properties");
        java.nio.file.Files.writeString(file, "delaySeconds=1\n");

        assertEquals(RtpBotSettingsStore.DEFAULT_DELAY_SECONDS,
                new RtpBotSettingsStore(file).loadDelaySeconds());
        assertThrows(IllegalArgumentException.class,
                () -> new RtpBotSettingsStore(file).saveDelaySeconds(1));
    }
}
