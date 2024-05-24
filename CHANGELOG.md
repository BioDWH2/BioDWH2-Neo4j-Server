# Changelog

## 📦 Version [v1.3.2](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.3.2)

Released: 24.05.2024

* ```[feature]``` Speedup database import
* ```[chore]``` Update dependencies

## 📦 Version [v1.3.1](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.3.1)

Released: 25.01.2023

* ```[feature]``` Update to Neo4j 5.X which requires Java 17 instead of Java 11 as before
* ```[chore]``` Update dependencies

## 📦 Version [v1.2.6](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.6)

Released: 13.09.2022

* ```[feature]``` Update to Neo4j 4.X which requires Java 11 instead of Java 8 as before
* ```[feature]``` Add import directory and allow export for apoc extensions
* ```[feature]``` Create or load neo4j configuration file for more config options
* ```[feature]``` Improved logging of node and edge creation
* ```[chore]``` Update dependencies

## 📦 Version [v1.2.5](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.5)

Released: 04.08.2021

 * ```[feature]``` BioDWH2 graph is now opened read-only during creation
 * ```[chore]``` Update dependencies

## 📦 Version [v1.2.4](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.4)

Released: 30.06.2021

 * ```[feature]``` Create all node indices in neo4j from graph
 * ```[chore]``` Update dependencies

## 📦 Version [v1.2.3](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.3)

Released: 25.06.2021

 * ```[feature]``` Reuse core BioDWH2Updater
 * ```[chore]``` Update dependencies

## 📦 Version [v1.2.2](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.2)

Released: 16.06.2021

 * ```[chore]``` Update dependencies

 > This is a breaking change release due to the new BioDWH2 core version v0.3.5. Only workspaces created with BioDWH2 v0.3.5 or later work with this version.

---

## 📦 Version [v1.2.1](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.1)

Released: 10.06.2021

 * ```[fix]``` Neo4j database service now properly shuts down after ```--create``` command
 * ```[fix]``` The APOC plugin is now loaded internally without using Neo4j plugin_dir configuration to prevent other jars in the same folder from being loaded
 * ```[chore]``` Update dependencies

---

## 📦 Version [v1.2.0](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.2.0)

Released: 01.03.2021

 * ```[fix]``` Neo4j browser download is now working again
 * ```[chore]``` Update dependencies

---

## 📦 Version [v1.1.8](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.8)

Released: 24.01.2021

 * ```[chore]``` Update dependencies

---

## 📦 Version [v1.1.7](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.7)

Released: 15.12.2020

 * ```[fix]``` Object arrays of unknown type are now mapped to string arrays
 * ```[chore]``` Update dependencies

---

## 📦 Version [v1.1.6](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.6)

Released: 20.11.2020

 * ```[feature]``` Check for newer BioDWH2-Neo4j-Server versions
 * ```[fix]``` Collection properties are now converted to arrays

---

## 📦 Version [v1.1.5](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.5)

Released: 18.11.2020

 * ```[fix]``` Fix github deployment action

---

## 📦 Version [v1.1.4](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.4)

Released: 18.11.2020

 * ```[fix]``` Ignore properties with null values

---

## 📦 Version [v1.1.3](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.3)

Released: 13.11.2020

 * ```[feature]``` Database hashing is now much faster
 * ```[fix]``` Fix release packaging with service resources

---

## 📦 Version [v1.1.2](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.2)

Released: 10.11.2020

 * ```[feature]``` Nodes and edges are now created in transaction batches

---

## 📦 Version [v1.1.1](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1.1)

Released: 04.11.2020

 * ```[fix]``` Ignore neo4j dependencies for dependabot due to 3.5 vs. 4.x versioning

---

## 📦 Version [v1.1](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.1)

Released: 04.11.2020

 * ```[feature]``` Check if the BioDWH2 database has changed and notify user to regenerate the Neo4j database
 * ```[feature]``` Add APOC plugin dependency
 * ```[feature]``` Add combined ```--create-start``` command
 * ```[chore]``` Update dependencies

---

## 📦 Version [v1.0](https://github.com/BioDWH2/BioDWH2-Neo4j-Server/releases/tag/v1.0)

Released: 08.10.2020