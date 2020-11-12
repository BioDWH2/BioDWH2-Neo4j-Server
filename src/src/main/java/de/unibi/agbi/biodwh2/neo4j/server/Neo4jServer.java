package de.unibi.agbi.biodwh2.neo4j.server;

import de.unibi.agbi.biodwh2.neo4j.server.model.CmdArgs;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Neo4jServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jServer.class);

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
        browser.downloadNeo4jBrowser();
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
            final String hash = HashUtils.getMd5HashFromFile(Paths.get(workspacePath, "sources/mapped.db").toString());
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
        browser.downloadNeo4jBrowser();
        browser.startNeo4jBrowser(commandLine.port);
    }

    private boolean checkNeo4jDatabaseMatchesWorkspace(final String workspacePath) {
        try {
            final String hash = HashUtils.getMd5HashFromFile(Paths.get(workspacePath, "sources/mapped.db").toString());
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
    }
}
