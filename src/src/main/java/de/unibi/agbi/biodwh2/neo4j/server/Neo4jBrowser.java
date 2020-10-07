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
import java.util.List;

final class Neo4jBrowser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jBrowser.class);
    private static final String NEO4J_BROWSER_RELEASE_URL = "https://api.github.com/repos/neo4j/neo4j-browser/releases";
    private static final String FALLBACK_DOWNLOAD_URL = "https://github.com/neo4j/neo4j-browser/releases/download/4.1.0/neo4j-browser-4.1.0.tgz";

    private final String workspacePath;

    public Neo4jBrowser(final String workspacePath) {
        this.workspacePath = workspacePath;
    }

    public boolean downloadNeo4jBrowser() {
        final File browserArchiveFile = Paths.get(workspacePath, "neo4j-browser.tgz").toFile();
        if (!browserArchiveFile.exists()) {
            final String downloadUrl = getNeo4jBrowserDownloadUrl();
            if (downloadUrl == null)
                return false;
            try {
                FileUtils.copyURLToFile(new URL(downloadUrl), browserArchiveFile);
            } catch (IOException e) {
                LOGGER.error("Failed to download neo4j-browser.", e);
                return false;
            }
        }
        return extractNeo4jBrowserArchive(browserArchiveFile);
    }

    private String getNeo4jBrowserDownloadUrl() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            final URL neo4jBrowserRelease = new URL(NEO4J_BROWSER_RELEASE_URL);
            List<GithubRelease> releases = mapper.readValue(neo4jBrowserRelease,
                                                            new TypeReference<List<GithubRelease>>() {
                                                            });
            for (GithubRelease release : releases)
                if (release.assets != null && release.assets.size() > 0)
                    return release.assets.get(0).browserDownloadUrl;
            return FALLBACK_DOWNLOAD_URL;
        } catch (IOException | ClassCastException e) {
            LOGGER.error("Failed to retrieve neo4j-browser download url.", e);
            return null;
        }
    }

    private boolean extractNeo4jBrowserArchive(final File browserArchiveFile) {
        final File destination = Paths.get(workspacePath, "neo4j-browser").toFile();
        final Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
        try {
            archiver.extract(browserArchiveFile, destination);
        } catch (IOException e) {
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
                LOGGER.error("Failed to open Browser", e);
            }
        }
    }

    private void configureJavalin(final JavalinConfig config) {
        final String wwwRoot = Paths.get(workspacePath, "neo4j-browser/package/dist").toString();
        config.addStaticFiles(wwwRoot, Location.EXTERNAL);
    }
}
