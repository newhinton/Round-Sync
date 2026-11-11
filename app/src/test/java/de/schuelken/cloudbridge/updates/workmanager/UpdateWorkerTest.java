package de.schuelken.cloudbridge.updates.workmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateWorkerTest {

    @Test
    public void requestUsesLatestStableEndpoint() {
        assertEquals("https://api.github.com/repos/thies2005/CloudBridge/releases/latest",
                UpdateWorker.latestReleaseRequest().url().toString());
    }

    @Test
    public void stableVersionIsNewerThanDevelopmentVersionAtLowerBase() {
        assertTrue(UpdateWorker.isNewerVersion(
                "1.0.1-beta.4293527",
                "v1.0.2"
        ));
    }

    @Test
    public void sameVersionIsNotNewer() {
        assertFalse(UpdateWorker.isNewerVersion("1.0.2", "v1.0.2"));
        assertFalse(UpdateWorker.isNewerVersion("1.0.3", "v1.0.2"));
    }

    @Test
    public void stableReleaseSupersedesSameVersionPrerelease() {
        assertTrue(UpdateWorker.isNewerVersion("1.0.2-beta.abc123", "v1.0.2"));
        assertFalse(UpdateWorker.isNewerVersion("1.0.2", "v1.0.2-beta.abc123"));
    }

    @Test
    public void cachedInstalledOlderAndLegacyTagsAreNotUpdates() {
        assertFalse(UpdateWorker.isNewerVersion("1.0.2", "v1.0.2"));
        assertFalse(UpdateWorker.isNewerVersion("1.0.2", "v1.0.1"));
        assertFalse(UpdateWorker.isNewerVersion("1.0.2", "beta-3.0"));
    }

    @Test
    public void malformedLegacyTagIsNotNewer() {
        assertFalse(UpdateWorker.isNewerVersion("1.0.1", "release-1.0.2"));
        assertFalse(UpdateWorker.isNewerVersion("1.0.1", "beta-3.0"));
    }

    @Test
    public void latestResponseParserSelectsTagAndChangelog() {
        UpdateWorker.LatestRelease release = UpdateWorker.parseLatestRelease(
                "{\"tag_name\":\"v1.0.2\",\"body\":\"Reliability fixes\"}"
        );

        assertEquals("v1.0.2", release.getTagName());
        assertEquals("Reliability fixes", release.getChangelog());
    }

    @Test
    public void latestResponseParserRejectsMalformedEmptyAndNonStableResponses() {
        assertNull(UpdateWorker.parseLatestRelease(null));
        assertNull(UpdateWorker.parseLatestRelease(""));
        assertNull(UpdateWorker.parseLatestRelease("{"));
        assertNull(UpdateWorker.parseLatestRelease("[{\"tag_name\":\"v1.0.2\"}]"));
        assertNull(UpdateWorker.parseLatestRelease("{\"tag_name\":\"v1.0.2\",\"prerelease\":true}"));
        assertNull(UpdateWorker.parseLatestRelease("{\"tag_name\":\"v1.0.2\",\"draft\":true}"));
        assertNull(UpdateWorker.parseLatestRelease("{\"body\":\"Missing tag\"}"));
    }
}
