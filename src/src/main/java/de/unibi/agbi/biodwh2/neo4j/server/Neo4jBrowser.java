package de.unibi.agbi.biodwh2.neo4j.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibi.agbi.biodwh2.neo4j.server.model.GithubRelease;
import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import io.javalin.http.staticfiles.Location;
import org.apache.commons.io.FileUtils;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Neo4jBrowser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jBrowser.class);
    private static final String NEO4J_BROWSER_RELEASE_URL = "https://api.github.com/repos/neo4j/neo4j-browser/releases";
    private static final String FALLBACK_DOWNLOAD_URL = "https://github.com/neo4j/neo4j-browser/releases/download/4.1.3/neo4j-browser-4.1.3.tgz";
    private static final String BROWSER_ARCHIVE_FILE_NAME = "neo4j-browser.tgz";

    private final String neo4jPath;
    private final String browserDistPath;

    public Neo4jBrowser(final String workspacePath) {
        neo4jPath = Paths.get(workspacePath, "neo4j").toString();
        browserDistPath = Paths.get(neo4jPath, "neo4j-browser/package/dist").toString();
    }

    public boolean downloadNeo4jBrowser() {
        final File browserArchiveFile = Paths.get(neo4jPath, BROWSER_ARCHIVE_FILE_NAME).toFile();
        if (!browserArchiveFile.exists()) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Downloading neo4j-browser...");
            boolean foundSuitableVersion = false;
            for (final String downloadUrl : getNeo4jBrowserDownloadUrlCandidates()) {
                try {
                    FileUtils.copyURLToFile(new URL(downloadUrl), browserArchiveFile);
                    final boolean extractedSuccessful = extractNeo4jBrowserArchive(browserArchiveFile);
                    if (extractedSuccessful && Paths.get(browserDistPath).toFile().exists()) {
                        foundSuitableVersion = true;
                        if (LOGGER.isInfoEnabled())
                            LOGGER.info("Suitable neo4j-browser version found: " + downloadUrl);
                        break;
                    }
                } catch (IOException ignored) {
                }
            }
            if (!foundSuitableVersion) {
                if (LOGGER.isErrorEnabled())
                    LOGGER.error("Failed to download neo4j-browser.");
            }
            return foundSuitableVersion;
        }
        return extractNeo4jBrowserArchive(browserArchiveFile);
    }

    private String[] getNeo4jBrowserDownloadUrlCandidates() {
        final TypeReference<List<GithubRelease>> releaseListType = new TypeReference<List<GithubRelease>>() {
        };
        final Set<String> candidates = new HashSet<>();
        candidates.add(FALLBACK_DOWNLOAD_URL);
        final ObjectMapper mapper = new ObjectMapper();
        try {
            final URL neo4jBrowserRelease = new URL(NEO4J_BROWSER_RELEASE_URL);
            final List<GithubRelease> releases = mapper.readValue(neo4jBrowserRelease, releaseListType);
            for (GithubRelease release : releases)
                if (release.assets != null && release.assets.size() > 0)
                    candidates.add(release.assets.get(0).browserDownloadUrl);
        } catch (IOException | ClassCastException e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to retrieve neo4j-browser download url.", e);
        }
        return candidates.stream().sorted(Comparator.reverseOrder()).toArray(String[]::new);
    }

    private boolean extractNeo4jBrowserArchive(final File browserArchiveFile) {
        final File destination = Paths.get(neo4jPath, "neo4j-browser").toFile();
        final Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
        try {
            archiver.extract(browserArchiveFile, destination);
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to extract zipped neo4j-browser file.", e);
            return false;
        }
        return true;
    }

    public void startNeo4jBrowser(Integer port) {
        if (port == null)
            port = 7474;
        Javalin.create(this::configureJavalin).start(port);
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI("http://localhost:" + port + "/"));
            } catch (IOException | URISyntaxException e) {
                if (LOGGER.isErrorEnabled())
                    LOGGER.error("Failed to open Browser", e);
            }
        }
    }

    private void configureJavalin(final JavalinConfig config) {
        config.addStaticFiles(browserDistPath, Location.EXTERNAL);
        config.showJavalinBanner = false;
    }
}
