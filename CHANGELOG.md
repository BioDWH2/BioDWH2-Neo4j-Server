# Changelog

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