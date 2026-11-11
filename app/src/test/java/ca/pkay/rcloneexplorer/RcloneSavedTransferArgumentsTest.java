package ca.pkay.rcloneexplorer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RcloneSavedTransferArgumentsTest {

    @Test
    public void savedTransferArgumentsPlaceOptionsBeforePathSeparator() {
        List<String> options = Arrays.asList("--transfers", "4", "--stats=1s");

        ArrayList<String> arguments = Rclone.savedTransferArguments(
                "sync",
                options,
                "--remote-source",
                "--remote-destination"
        );

        assertEquals(
                Arrays.asList(
                        "sync",
                        "--transfers",
                        "4",
                        "--stats=1s",
                        "--",
                        "--remote-source",
                        "--remote-destination"
                ),
                arguments
        );
    }
}
