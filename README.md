![Java CI](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/workflows/Java%20CI/badge.svg?branch=develop) ![Release](https://img.shields.io/github/v/release/BioDWH2/BioDWH2-Neo4j-Server) ![Downloads](https://img.shields.io/github/downloads/BioDWH2/BioDWH2-Neo4j-Server/total) ![License](https://img.shields.io/github/license/BioDWH2/BioDWH2-Neo4j-Server)

# BioDWH2-Neo4j-Server
**BioDWH2** is an easy-to-use, automated, graph-based data warehouse and mapping tool for bioinformatics and medical informatics. The main repository can be found [here](https://github.com/BioDWH2/BioDWH2).

This repository contains the **BioDWH2-Neo4j-Server** utility which can be used to create and explore a Neo4j graph database from any BioDWH2 workspace. There is no need for any Neo4j installation. All necessary components are bundled with this tool.

## Download
The latest release version of **BioDWH2-Neo4j-Server** can be downloaded [here](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/latest).

## Usage
> ⚠️️ BioDWH2-Neo4j-Server is built upon Neo4j 5.X which requires the Java Runtime Environment (JRE) version 17 or higher. The JRE 17 is available [here](https://adoptium.net).

Creating a database from any workspace is done using the following command. Every time the workspace is updated or changed, the create command has to be executed again.
~~~BASH
> java -jar BioDWH2-Neo4j-Server.jar --create /path/to/workspace
~~~

Once the database has been created, the database and Neo4j-Browser can be started as follows:
~~~BASH
> java -jar BioDWH2-Neo4j-Server.jar --start /path/to/workspace
~~~

Optionally, the ports for the Neo4j-Browser and Neo4j bolt protocol can be adjusted using the port and bolt-port command line arguments.

The Neo4j Browser which opens automatically can be used to connect to the Neo4j database without authentication. 

## Help
~~~
Usage: BioDWH2-Neo4j-Server.jar [-h] [-bp=<boltPort>] [-c=<workspacePath>]
                                [-cs=<workspacePath>] [-p=<port>]
                                [-s=<workspacePath>]
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