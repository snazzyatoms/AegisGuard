package com.aegisguard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared source-loading helpers for contract tests.
 *
 * <p>The {@code commands} package is split across several files (a dispatch shell plus
 * feature-oriented handlers). Contract tests assert on the command layer as a whole, so
 * this helper concatenates every source file in the package regardless of which file a
 * given behavior lives in.
 */
public final class TestSources {

    private TestSources() {}

    /** Concatenated source of every {@code .java} file in the commands package. */
    public static String commandsPackage() {
        return packageSources(Path.of("src/main/java/com/aegisguard/commands"));
    }

    private static String packageSources(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.java")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files.sort(Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            try {
                sb.append(Files.readString(file)).append('\n');
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sb.toString();
    }
}
