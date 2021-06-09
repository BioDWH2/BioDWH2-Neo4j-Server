package de.unibi.agbi.biodwh2.neo4j.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibi.agbi.biodwh2.core.model.Version;
import de.unibi.agbi.biodwh2.neo4j.server.model.CmdArgs;
import de.unibi.agbi.biodwh2.neo4j.server.model.GithubAsset;
import de.unibi.agbi.biodwh2.neo4j.server.model.GithubRelease;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;

public class Neo4jServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jServer.class);
    private static final String BIODWH2_NEO4J_RELEASE_URL = "https://api.github.com/repos/BioDWH2/BioDWH2-Neo4j-Server/releases";

    private Neo4jServer() {
    }

    public static void main(final String... args) {
        final CmdArgs commandLine = parseCommandLine(args);
        new Neo4jServer().run(commandLine);
    }

    private static CmdArgs parseCommandLine(final String... args) {
        final CmdArgs result = new CmdArgs();
        final CommandLine cmd = new CommandLine(result);
        cmd.parseArgs(args);
        return result;
    }

    private void run(final CmdArgs commandLine) {
        checkForUpdate();
        if (commandLine.createStart != null)
            createAndStartWorkspaceServer(commandLine);
        else if (commandLine.start != null)
            startWorkspaceServer(commandLine);
        else if (commandLine.create != null)
            createWorkspaceDatabase(commandLine);
        else
            printHelp(commandLine);
    }

    private void createAndStartWorkspaceServer(final CmdArgs commandLine) {
        final String workspacePath = commandLine.createStart;
        if (!verifyWorkspaceExists(workspacePath)) {
            printHelp(commandLine);
            return;
        }
        final Neo4jService service = new Neo4jService(workspacePath);
        service.deleteOldDatabase();
        service.startNeo4jService(commandLine.boltPort);
        service.createDatabase();
        storeWorkspaceHash(workspacePath);
        final Neo4jBrowser browser = new Neo4jBrowser(workspacePath);
        if (browser.downloadNeo4jBrowser())
            browser.startNeo4jBrowser(commandLine.port);
    }

    private boolean verifyWorkspaceExists(final String workspacePath) {
        if (StringUtils.isEmpty(workspacePath) || !Paths.get(workspacePath).toFile().exists()) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Workspace path '" + workspacePath + "' was not found");
            return false;
        }
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Using workspace directory '" + workspacePath + "'");
        return true;
    }

    private void printHelp(final CmdArgs commandLine) {
        CommandLine.usage(commandLine, System.out);
    }

    private void storeWorkspaceHash(final String workspacePath) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Updating workspace neo4j cache checksum...");
        final Path hashFilePath = Paths.get(workspacePath, "neo4j/checksum.txt");
        try {
            final String hash = HashUtils.getFastPseudoHashFromFile(
                    Paths.get(workspacePath, "sources/mapped.db").toString());
            final FileWriter writer = new FileWriter(hashFilePath.toFile());
            writer.write(hash);
            writer.close();
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to store hash of workspace mapped graph", e);
        }
    }

    private void startWorkspaceServer(final CmdArgs commandLine) {
        final String workspacePath = commandLine.start;
        if (!verifyWorkspaceExists(workspacePath)) {
            printHelp(commandLine);
            return;
        }
        if (!checkNeo4jDatabaseMatchesWorkspace(workspacePath) && LOGGER.isInfoEnabled())
            LOGGER.warn("The neo4j database is out-of-date and should be recreated with the --create command");
        final Neo4jService service = new Neo4jService(workspacePath);
        service.startNeo4jService(commandLine.boltPort);
        final Neo4jBrowser browser = new Neo4jBrowser(workspacePath);
        if (browser.downloadNeo4jBrowser())
            browser.startNeo4jBrowser(commandLine.port);
    }

    private boolean checkNeo4jDatabaseMatchesWorkspace(final String workspacePath) {
        try {
            final String hash = HashUtils.getFastPseudoHashFromFile(
                    Paths.get(workspacePath, "sources/mapped.db").toString());
            final Path hashFilePath = Paths.get(workspacePath, "neo4j/checksum.txt");
            if (Files.exists(hashFilePath)) {
                final String storedHash = new String(Files.readAllBytes(hashFilePath)).trim();
                return hash.equals(storedHash);
            }
        } catch (IOException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Failed to check hash of workspace mapped graph", e);
        }
        return false;
    }

    private void createWorkspaceDatabase(final CmdArgs commandLine) {
        final String workspacePath = commandLine.create;
        if (!verifyWorkspaceExists(workspacePath)) {
            printHelp(commandLine);
            return;
        }
        final Neo4jService service = new Neo4jService(workspacePath);
        service.deleteOldDatabase();
        service.startNeo4jService(commandLine.boltPort);
        service.createDatabase();
        storeWorkspaceHash(workspacePath);
        LOGGER.info("Neo4j database successfully created. Shutting down...");
        service.shutdown();
    }

    private void checkForUpdate() {
        final Version currentVersion = getCurrentVersion();
        Version mostRecentVersion = null;
        String mostRecentDownloadUrl = null;
        final ObjectMapper mapper = new ObjectMapper();
        try {
            final URL releaseUrl = new URL(BIODWH2_NEO4J_RELEASE_URL);
            final List<GithubRelease> releases = mapper.readValue(releaseUrl, new TypeReference<List<GithubRelease>>() {
            });
            for (final GithubRelease release : releases) {
                final Version version = Version.tryParse(release.tagName.replace("v", ""));
                if (version != null) {
                    final String jarName = "BioDWH2-Neo4j-Server-" + release.tagName + ".jar";
                    final Optional<GithubAsset> jarAsset = release.assets.stream().filter(
                            asset -> asset.name.equalsIgnoreCase(jarName)).findFirst();
                    if (jarAsset.isPresent() && mostRecentVersion == null || version.compareTo(mostRecentVersion) > 0) {
                        mostRecentVersion = version;
                        //noinspection OptionalGetWithoutIsPresent
                        mostRecentDownloadUrl = jarAsset.get().browserDownloadUrl;
                    }
                }
            }
        } catch (IOException | ClassCastException ignored) {
        }
        if (currentVersion == null && mostRecentVersion != null || currentVersion != null && currentVersion.compareTo(
                mostRecentVersion) < 0) {
            LOGGER.info("=======================================");
            LOGGER.info("New version " + mostRecentVersion + " of BioDWH2-Neo4j-Server is available at:");
            LOGGER.info(mostRecentDownloadUrl);
            LOGGER.info("=======================================");
        }
    }

    private Version getCurrentVersion() {
        try {
            final Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try {
                    final Manifest manifest = new Manifest(resources.nextElement().openStream());
                    final Version version = Version.tryParse(manifest.getMainAttributes().getValue("BioDWH2-version"));
                    if (version != null)
                        return version;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
