package de.unibi.agbi.biodwh2.neo4j.server;

import apoc.ApocConfiguration;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.kernel.configuration.BoltConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

class Neo4jService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jService.class);

    private final String workspacePath;
    private final String neo4jPath;
    private final String databasePath;
    private GraphDatabaseService dbService;

    public Neo4jService(final String workspacePath) {
        this.workspacePath = workspacePath;
        neo4jPath = Paths.get(workspacePath, "neo4j").toString();
        databasePath = Paths.get(neo4jPath, "neo4j.db").toString();
    }

    public void startNeo4jService(Integer port) {
        if (port == null)
            port = 8083;
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Starting Neo4j DBMS on bolt://localhost:" + port + "...");
        BoltConnector bolt = new BoltConnector("0");
        GraphDatabaseBuilder builder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(
                Paths.get(databasePath).toFile());
        builder.setConfig(GraphDatabaseSettings.auth_enabled, "false");
        builder.setConfig(GraphDatabaseSettings.pagecache_memory, "512M");
        builder.setConfig(GraphDatabaseSettings.string_block_size, "60");
        builder.setConfig(GraphDatabaseSettings.array_block_size, "300");
        builder.setConfig(GraphDatabaseSettings.store_internal_log_level, "DEBUG");
        builder.setConfig(bolt.enabled, "true").setConfig(bolt.type, "BOLT");
        builder.setConfig(bolt.listen_address, "0.0.0.0:" + port);
        builder.setConfig(bolt.encryption_level, BoltConnector.EncryptionLevel.OPTIONAL.toString());
        builder.setConfig(GraphDatabaseSettings.plugin_dir, getApocPluginPath());
        builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, "apoc.*");
        dbService = builder.newGraphDatabase();
        Runtime.getRuntime().addShutdownHook(new Thread(dbService::shutdown));
    }

    private String getApocPluginPath() {
        try {
            return new File(ApocConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().toPath().toString();
        } catch (URISyntaxException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Failed to register APOC procedures", e);
        }
        return null;
    }

    public void deleteOldDatabase() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Removing old database...");
        try {
            FileUtils.deleteDirectory(Paths.get(neo4jPath, "certificates").toFile());
            FileUtils.deleteDirectory(Paths.get(neo4jPath, "logs").toFile());
            FileUtils.deleteDirectory(new File(databasePath));
            FileUtils.deleteQuietly(Paths.get(neo4jPath, "store_lock").toFile());
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to remove old database '" + neo4jPath + "'", e);
        }
    }

    public void createDatabase() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating Neo4j database...");
        final Graph graph = new Graph(Paths.get(workspacePath, "sources/mapped.db").toString(), true);
        try (Transaction tx = dbService.beginTx()) {
            final HashMap<Long, Long> nodeIdNeo4jIdMap = createNeo4jNodes(graph);
            createNeo4jEdges(graph, nodeIdNeo4jIdMap);
            tx.success();
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j database '" + databasePath + "'", e);
        } finally {
            graph.dispose();
        }
        createNeo4jIndices();
    }

    private HashMap<Long, Long> createNeo4jNodes(final Graph graph) {
        final HashMap<Long, Long> nodeIdNeo4jIdMap = new HashMap<>();
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating nodes...");
        for (final Node node : graph.getNodes()) {
            final org.neo4j.graphdb.Node neo4jNode = dbService.createNode();
            for (final String propertyKey : node.getPropertyKeys())
                setPropertySafe(node, neo4jNode, propertyKey);
            nodeIdNeo4jIdMap.put(node.getId(), neo4jNode.getId());
            neo4jNode.addLabel(Label.label(node.getLabel()));
        }
        return nodeIdNeo4jIdMap;
    }

    private void setPropertySafe(final Node node, final org.neo4j.graphdb.Node neo4jNode, final String propertyKey) {
        try {
            if (isPropertyAllowed(propertyKey)) {
                Object value = node.getProperty(propertyKey);
                // TODO
                if (value instanceof List) {
                    value = ((List<String>) value).toArray(new String[0]);
                }
                neo4jNode.setProperty(propertyKey, value);
            }
        } catch (IllegalArgumentException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn(
                        "Illegal property '" + propertyKey + " -> " + node.getProperty(propertyKey) + "' for node '" +
                        node.getId() + "[" + node.getLabel() + "]'");
        }
    }

    private boolean isPropertyAllowed(final String name) {
        return !"_modified".equals(name) && !"_revision".equals(name) && !"__label".equals(name) && !"_id".equals(name);
    }

    private void createNeo4jEdges(final Graph graph, final HashMap<Long, Long> nodeIdNeo4jIdMap) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating edges...");
        for (final Edge edge : graph.getEdges()) {
            final RelationshipType relationshipType = RelationshipType.withName(edge.getLabel());
            final org.neo4j.graphdb.Node fromNode = dbService.getNodeById(nodeIdNeo4jIdMap.get(edge.getFromId()));
            final org.neo4j.graphdb.Node toNode = dbService.getNodeById(nodeIdNeo4jIdMap.get(edge.getToId()));
            final Relationship relationship = fromNode.createRelationshipTo(toNode, relationshipType);
            for (final String propertyKey : edge.getPropertyKeys())
                if (isPropertyAllowed(propertyKey))
                    relationship.setProperty(propertyKey, edge.getProperty(propertyKey));
        }
    }

    private void createNeo4jIndices() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating indices...");
        try (Transaction tx = dbService.beginTx()) {
            Schema schema = dbService.schema();
            for (Label label : dbService.getAllLabels())
                schema.constraintFor(label).assertPropertyIsUnique("__id").create();
            tx.success();
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j database '" + databasePath + "'", e);
        }
    }
}
