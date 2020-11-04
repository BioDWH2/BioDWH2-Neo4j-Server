# BioDWH2-Neo4j-Server
**BioDWH2** is an easy-to-use, automated, graph-based data warehouse and mapping tool for bioinformatics and medical informatics. The main repository can be found [here](https://github.com/BioDWH2/BioDWH2).

This repository contains the **BioDWH2-Neo4j-Server** utility which can be used to create and explore a Neo4j graph database from any BioDWH2 workspace. There is no need for any Neo4j installation. All necessary components are bundled with this tool.

## Usage
Creating a database from any workspace is done using the following command. Every time the workspace is updated or changed, the create command has to be executed again.
~~~BASH
> BioDWH2-Neo4j-Server.jar --create /path/to/workspace
~~~

Once the database has been created, the database and Neo4j-Browser can be started as follows:
~~~BASH
> BioDWH2-Neo4j-Server.jar --start /path/to/workspace
~~~

Optionally, the ports for the Neo4j-Browser and Neo4j bolt protocol can be adjusted using the port and bolt-port command line arguments.

## Help
~~~
Usage: BioDWH2-Neo4j-Server.jar [-h] [-bp=<boltPort>] [-c=<workspacePath>]
                                [-p=<port>] [-s=<workspacePath>]
  -bp, --bolt-port=<boltPort>
                      Specifies the Neo4j bolt port (default 8083)
  -c, --create=<workspacePath>
                      Create a Neo4j database from the workspace graph
  -cs, --create-start=<workspacePath>
                      Create and start a Neo4j database from the workspace graph
  -h, --help          print this message
  -p, --port=<port>   Specifies the Neo4j browser port (default 7474)
  -s, --start=<workspacePath>
                      Start a Neo4j server for the workspace
~~~