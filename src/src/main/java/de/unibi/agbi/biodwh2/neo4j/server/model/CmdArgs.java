package de.unibi.agbi.biodwh2.neo4j.server.model;

import picocli.CommandLine;

@CommandLine.Command(name = "BioDWH2-Neo4j-Server.jar")
public class CmdArgs {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "print this message")
    public boolean help;
    @CommandLine.Option(names = {
            "-s", "--start"
    }, arity = "1", paramLabel = "<workspacePath>", description = "Start a Neo4j server for the workspace")
    public String start;
    @CommandLine.Option(names = {
            "-c", "--create"
    }, arity = "1", paramLabel = "<workspacePath>", description = "Create a Neo4j database from the workspace graph")
    public String create;
    @CommandLine.Option(names = {
            "-cs", "--create-start"
    }, arity = "1", paramLabel = "<workspacePath>", description = "Create and start a Neo4j database from the workspace graph")
    public String createStart;
    @CommandLine.Option(names = {
            "-p", "--port"
    }, defaultValue = "7474", paramLabel = "<port>", description = "Specifies the Neo4j browser port (default 7474)")
    public Integer port;
    @CommandLine.Option(names = {
            "-bp", "--bolt-port"
    }, defaultValue = "8083", paramLabel = "<boltPort>", description = "Specifies the Neo4j bolt port (default 8083)")
    public Integer boltPort;
}
