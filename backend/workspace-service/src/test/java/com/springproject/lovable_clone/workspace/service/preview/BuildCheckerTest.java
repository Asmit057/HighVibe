package com.springproject.lovable_clone.workspace.service.preview;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BuildCheckerTest {

    @Test
    void buildOutputLosesColoursAndTheBuildFoldersPath() {
        Path dir = Path.of("D:", "work", "previews", ".check", "abc123").toAbsolutePath();
        String raw = "\u001B[31merror during build:\u001B[39m\n"
                + "[vite:esbuild] Transform failed with 1 error:\n"
                + dir + java.io.File.separator + "src" + java.io.File.separator + "data.ts:211:1: ERROR: Unexpected \"/\"\n"
                + "file: " + dir.toString().replace('\\', '/') + "/src/data.ts:211:1\n"
                + "    at failureErrorWithLog (node_modules\\esbuild\\lib\\main.js:1467:15)\n"
                + "    at Socket.emit (node:events:518:28)\n";

        String clean = BuildChecker.cleanOutput(raw, dir);

        assertThat(clean).startsWith("error during build:")
                .contains("ERROR: Unexpected \"/\"")
                .endsWith("file: src/data.ts:211:1")
                .doesNotContain("\u001B")
                .doesNotContain("previews")
                .doesNotContain("failureErrorWithLog");
    }

    @Test
    void veryLongOutputIsCut() {
        String clean = BuildChecker.cleanOutput("x".repeat(10_000), Path.of("D:", "dir").toAbsolutePath());

        assertThat(clean).hasSize(3000 + "\n...".length()).endsWith("...");
    }
}
