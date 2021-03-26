package de.unibi.agbi.biodwh2.neo4j.server;

import apoc.ApocConfig;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import org.apache.commons.io.FileUtils;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

class Neo4jService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jService.class);

    private final String workspacePath;
    private final String neo4jPath;
    private final String databasePath;
    private GraphDatabaseService dbService;
    private DatabaseManagementService managementService;

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
        final DatabaseManagementServiceBuilder builder = new DatabaseManagementServiceBuilder(Paths.get(databasePath));
        builder.setConfig(GraphDatabaseSettings.auth_enabled, false);
        builder.setConfig(GraphDatabaseSettings.pagecache_memory, "512M");
        builder.setConfig(GraphDatabaseSettings.store_internal_log_level, Level.DEBUG);
        builder.setConfig(BoltConnector.enabled, true);
        builder.setConfig(BoltConnector.listen_address, new SocketAddress("0.0.0.0", port));
        builder.setConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED);
        builder.setConfig(GraphDatabaseSettings.plugin_dir, getApocPluginPath());
        builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, Collections.singletonList("apoc.*"));
        builder.setConfig(GraphDatabaseSettings.procedure_allowlist, Collections.singletonList("apoc.*"));
        managementService = builder.build();
        dbService = managementService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
    }

    private Path getApocPluginPath() {
        try {
            return new File(ApocConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile().toPath();
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
        try (Graph graph = new Graph(Paths.get(workspacePath, "sources/mapped.db"), true)) {
            final HashMap<Long, Long> nodeIdNeo4jIdMap = createNeo4jNodes(graph);
            createNeo4jEdges(graph, nodeIdNeo4jIdMap);
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j database '" + databasePath + "'", e);
        }
        createNeo4jIndices();
    }

    private HashMap<Long, Long> createNeo4jNodes(final Graph graph) {
        final HashMap<Long, Long> nodeIdNeo4jIdMap = new HashMap<>();
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating nodes...");
        batchIterate(graph.getNodes(), nodes -> {
            final Transaction tx = dbService.beginTx();
            for (final Node node : nodes) {
                final org.neo4j.graphdb.Node neo4jNode = tx.createNode();
                for (final String propertyKey : node.keySet())
                    setPropertySafe(node, neo4jNode, propertyKey);
                nodeIdNeo4jIdMap.put(node.getId(), neo4jNode.getId());
                for (final String label : node.getLabels())
                    neo4jNode.addLabel(Label.label(label));
            }
            tx.commit();
        });
        return nodeIdNeo4jIdMap;
    }

    private <T> void batchIterate(final Iterable<T> iterable, Consumer<List<T>> consumer) {
        List<T> currentBatch = new ArrayList<>();
        for (T element : iterable) {
            currentBatch.add(element);
            if (currentBatch.size() == 1000) {
                consumer.accept(currentBatch);
                currentBatch.clear();
            }
        }
        if (currentBatch.size() > 0)
            consumer.accept(currentBatch);
    }

    private void setPropertySafe(final Node node, final org.neo4j.graphdb.Node neo4jNode, final String propertyKey) {
        try {
            if (!Node.IGNORED_FIELDS.contains(propertyKey)) {
                Object value = node.getProperty(propertyKey);
                if (value instanceof Collection)
                    value = convertCollectionToArray((Collection<?>) value);
                if (value != null)
                    neo4jNode.setProperty(propertyKey, value);
            }
        } catch (IllegalArgumentException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn(
                        "Illegal property '" + propertyKey + " -> " + node.getProperty(propertyKey) + "' for node '" +
                        node.getId() + "[" + String.join(":", node.getLabels()) + "]'");
        }
    }

    @SuppressWarnings({"SuspiciousToArrayCall"})
    private Object convertCollectionToArray(final Collection<?> collection) {
        Class<?> type = null;
        for (Object t : collection) {
            if (t != null) {
                type = t.getClass();
                break;
            }
        }
        if (type != null) {
            if (type.equals(String.class))
                return collection.stream().map(type::cast).toArray(String[]::new);
            if (type.equals(Boolean.class))
                return collection.stream().map(type::cast).toArray(Boolean[]::new);
            if (type.equals(Integer.class))
                return collection.stream().map(type::cast).toArray(Integer[]::new);
            if (type.equals(Float.class))
                return collection.stream().map(type::cast).toArray(Float[]::new);
            if (type.equals(Long.class))
                return collection.stream().map(type::cast).toArray(Long[]::new);
            if (type.equals(Double.class))
                return collection.stream().map(type::cast).toArray(Double[]::new);
            if (type.equals(Byte.class))
                return collection.stream().map(type::cast).toArray(Byte[]::new);
            if (type.equals(Short.class))
                return collection.stream().map(type::cast).toArray(Short[]::new);
        }
        return collection.stream().map(Object::toString).toArray(String[]::new);
    }

    private void createNeo4jEdges(final Graph graph, final HashMap<Long, Long> nodeIdNeo4jIdMap) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating edges...");
        batchIterate(graph.getEdges(), edges -> {
            final Transaction tx = dbService.beginTx();
            for (final Edge edge : edges) {
                final RelationshipType relationshipType = RelationshipType.withName(edge.getLabel());
                final org.neo4j.graphdb.Node fromNode = tx.getNodeById(nodeIdNeo4jIdMap.get(edge.getFromId()));
                final org.neo4j.graphdb.Node toNode = tx.getNodeById(nodeIdNeo4jIdMap.get(edge.getToId()));
                final Relationship relationship = fromNode.createRelationshipTo(toNode, relationshipType);
                for (final String propertyKey : edge.keySet())
                    if (!Edge.IGNORED_FIELDS.contains(propertyKey)) {
                        Object value = edge.getProperty(propertyKey);
                        if (value instanceof Collection)
                            value = convertCollectionToArray((Collection<?>) value);
                        if (value != null)
                            relationship.setProperty(propertyKey, value);
                    }
            }
            tx.commit();
        });
    }

    private void createNeo4jIndices() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating indices...");
        try (Transaction tx = dbService.beginTx()) {
            Schema schema = tx.schema();
            for (Label label : tx.getAllLabels()) {
                if (LOGGER.isInfoEnabled())
                    LOGGER.info("Creating unique index on '__id' field for label '" + label + "'...");
                schema.constraintFor(label).assertPropertyIsUnique("__id").create();
            }
            tx.commit();
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j database '" + databasePath + "'", e);
        }
    }
}
