package de.unibi.agbi.biodwh2.neo4j.server;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

final class HashUtils {
    private HashUtils() {
    }

    static String getMd5HashFromFile(final String filePath) throws IOException {
        try (final InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            return DigestUtils.md5Hex(inputStream);
        }
    }
}
