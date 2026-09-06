package ca.pkay.rcloneexplorer.Items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Unit tests for {@link RemoteItem} backend type resolution (RC-28 / Track 4A).
 *
 * <p>{@code rclone listremotes} returns configured remote names, not backend type names, so these
 * tests exercise {@code RemoteItem}'s string->int type mapping directly against known backend type
 * strings instead of shelling out to rclone.
 */
public class RemoteItemTest {

    @Test
    public void internxtBackendResolvesToDedicatedType() {
        RemoteItem item = new RemoteItem("my-internxt", "internxt");
        assertEquals("internxt should no longer fall through to -1", RemoteItem.INTERNXT, item.getType());
        assertEquals("internxt", item.getTypeReadable());
    }

    @Test
    public void drimeBackendResolvesToDedicatedType() {
        RemoteItem item = new RemoteItem("my-drime", "drime");
        assertEquals("drime should no longer fall through to -1", RemoteItem.DRIME, item.getType());
        assertEquals("drime", item.getTypeReadable());
    }

    @Test
    public void internxtAndDrimeAreDistinctTypes() {
        // Regression: previously both resolved to -1, so two differently-typed remotes with the
        // same name would compare equal and share the generic cloud icon.
        RemoteItem internxt = new RemoteItem("same-name", "internxt");
        RemoteItem drime = new RemoteItem("same-name", "drime");
        assertNotEquals(internxt.getType(), drime.getType());
        assertFalse(internxt.equals(drime));
        assertFalse(internxt.hashCode() == drime.hashCode());
    }

    @Test
    public void knownBackendsStillResolve() {
        // Ensure the new entries did not perturb existing mappings.
        assertEquals(RemoteItem.WEBDAV, new RemoteItem("w", "webdav").getType());
        assertEquals(RemoteItem.GOOGLE_DRIVE, new RemoteItem("g", "drive").getType());
        assertEquals(RemoteItem.S3, new RemoteItem("s", "s3").getType());
        assertEquals(RemoteItem.SFTP, new RemoteItem("ssh", "sftp").getType());
    }

    @Test
    public void unknownBackendFallsBackToUnmapped() {
        assertEquals(-1, new RemoteItem("u", "not-a-real-backend").getType());
    }
}
