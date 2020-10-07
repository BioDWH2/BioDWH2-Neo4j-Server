package de.unibi.agbi.biodwh2.neo4j.server;

import de.unibi.agbi.biodwh2.neo4j.server.model.CmdArgs;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

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
        if (commandLine.start != null)
            startWorkspaceServer(commandLine);
        else if (commandLine.create != null)
            createWorkspaceDatabase(commandLine);
        else
            printHelp(commandLine);
    }

    private void startWorkspaceServer(final CmdArgs commandLine) {
        final String workspacePath = commandLine.start;
        if (StringUtils.isEmpty(workspacePath) || !Paths.get(workspacePath).toFile().exists()) {
            LOGGER.error("Workspace path '" + workspacePath + "' was not found");
            printHelp(commandLine);
            return;
        }
        final Neo4jBrowser browser = new Neo4jBrowser(workspacePath);
        browser.downloadNeo4jBrowser();
        browser.startNeo4jBrowser(commandLine.port);
    }

    private void createWorkspaceDatabase(final CmdArgs commandLine) {
        final String workspacePath = commandLine.start;
        if (StringUtils.isEmpty(workspacePath) || !Paths.get(workspacePath).toFile().exists()) {
            LOGGER.error("Workspace path '" + workspacePath + "' was not found");
            printHelp(commandLine);
            return;
        }
    }

    private void printHelp(final CmdArgs commandLine) {
        CommandLine.usage(commandLine, System.out);
    }
}
