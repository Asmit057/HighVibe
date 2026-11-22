package com.springproject.lovable_clone.workspace.service;

import com.springproject.lovable_clone.common.error.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilePathsTest {

    @ParameterizedTest
    @CsvSource({
            "src/App.tsx, src/App.tsx",
            "/src/App.tsx, src/App.tsx",
            "./index.html, index.html",
            "src/components/ui/Button.tsx, src/components/ui/Button.tsx",
            "public/hero image.png, public/hero image.png"
    })
    void normalizesValidPaths(String raw, String expected) {
        assertThat(FilePaths.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "../etc/passwd", "src/../../x", "src\\App.tsx", "src//App.tsx",
            "src/", "node_modules/react/index.js", ".git/config", "src/a\u0000b"})
    void rejectsPathsThatCouldEscapeTheProject(String raw) {
        assertThatThrownBy(() -> FilePaths.normalize(raw)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @CsvSource({"src/App.tsx, text/javascript", "index.html, text/html", "src/index.css, text/css", "package.json, application/json"})
    void guessesContentTypes(String path, String type) {
        assertThat(FilePaths.contentType(path)).isEqualTo(type);
    }
}
