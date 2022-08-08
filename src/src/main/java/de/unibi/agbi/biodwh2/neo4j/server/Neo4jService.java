package de.unibi.agbi.biodwh2.neo4j.server;

import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.IndexDescription;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import org.apache.commons.io.FileUtils;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.SettingImpl;
import org.neo4j.configuration.SettingValueParsers;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.connectors.HttpsConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

class Neo4jService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jService.class);

    private final String workspacePath;
    private final String neo4jPath;
    private final Path databasePath;
    private final String importPath;
    private DatabaseManagementService managementService;
    private GraphDatabaseService dbService;

    public Neo4jService(final String workspacePath) {
        this.workspacePath = workspacePath;
        neo4jPath = Paths.get(workspacePath, "neo4j").toString();
        databasePath = Paths.get(neo4jPath, "neo4j.db");
        importPath = Paths.get(neo4jPath, "import").toString();
    }

    public void startNeo4jService(final Integer boltPort) {
        Paths.get(neo4jPath).toFile().mkdir();
        Paths.get(importPath).toFile().mkdir();
        final Config config = createAndStoreDefaultConfig(boltPort);
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Starting Neo4j DBMS on bolt://" + config.get(BoltConnector.listen_address) + "...");
        final Map<Setting<?>, Object> configRaw = new HashMap<>(config.getValues());
        managementService = new DatabaseManagementServiceBuilder(databasePath).setConfig(configRaw).build();
        dbService = managementService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
        registerApocProcedures();
    }

    private Config createAndStoreDefaultConfig(final Integer boltPort) {
        final Config.Builder builder = Config.newBuilder().setDefaults(GraphDatabaseSettings.SERVER_DEFAULTS);
        builder.set(GraphDatabaseSettings.pagecache_memory, "512M");
        builder.set(GraphDatabaseSettings.store_internal_log_level, Level.DEBUG);
        builder.set(HttpConnector.enabled, false);
        builder.set(HttpsConnector.enabled, false);
        builder.set(BoltConnector.enabled, true);
        builder.set(BoltConnector.listen_address, new SocketAddress("0.0.0.0", boltPort == null ? 8083 : boltPort));
        builder.set(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED);
        builder.set(SettingImpl.newBuilder("dbms.ssl.policy.bolt.enabled", SettingValueParsers.BOOL, false).build(),
                    false);
        builder.set(GraphDatabaseSettings.auth_enabled, false);
        builder.set(GraphDatabaseSettings.procedure_unrestricted, Collections.singletonList("apoc.*"));
        builder.set(GraphDatabaseSettings.procedure_allowlist, Collections.singletonList("apoc.*"));
        builder.set(SettingImpl.newBuilder("dbms.directories.import", SettingValueParsers.PATH, null).build(),
                    Paths.get(importPath));
        builder.set(SettingImpl.newBuilder("apoc.import.file.enabled", SettingValueParsers.BOOL, false).build(), true);
        builder.set(SettingImpl.newBuilder("apoc.export.file.enabled", SettingValueParsers.BOOL, false).build(), true);
        return builder.build();
    }

    private void registerApocProcedures() {
        final GlobalProcedures procedures = ((GraphDatabaseAPI) dbService).getDependencyResolver().resolveDependency(
                GlobalProcedures.class);
        final List<Class<?>> apocClasses = Factory.getInstance().loadAllClasses("apoc.");
        apocClasses.forEach((proc) -> {
            try {
                procedures.registerProcedure(proc);
            } catch (Throwable ignored) {
            }
        });
    }

    public void deleteOldDatabase() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Removing old database...");
        try {
            FileUtils.deleteDirectory(Paths.get(neo4jPath, "certificates").toFile());
            FileUtils.deleteDirectory(Paths.get(neo4jPath, "logs").toFile());
            FileUtils.deleteDirectory(databasePath.toFile());
            FileUtils.deleteQuietly(Paths.get(neo4jPath, "store_lock").toFile());
        } catch (IOException e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to remove old database '" + neo4jPath + "'", e);
        }
    }

    public void shutdown() {
        managementService.shutdown();
    }

    public void createDatabase() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating Neo4j database...");
        try (Graph graph = new Graph(Paths.get(workspacePath, "sources/mapped.db"), true, true)) {
            final HashMap<Long, Long> nodeIdNeo4jIdMap = createNeo4jNodes(graph);
            createNeo4jEdges(graph, nodeIdNeo4jIdMap);
            createNeo4jIndices(graph);
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j database '" + databasePath + "'", e);
        }
    }

    private HashMap<Long, Long> createNeo4jNodes(final Graph graph) {
        final HashMap<Long, Long> nodeIdNeo4jIdMap = new HashMap<>();
        final String[] labels = graph.getNodeLabels();
        for (int i = 0; i < labels.length; i++) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Creating nodes with label '" + labels[i] + "' (" + (i + 1) + "/" + labels.length + ")...");
            batchIterate(graph.getNodes(labels[i]), nodes -> {
                try (Transaction tx = dbService.beginTx()) {
                    for (final Node node : nodes) {
                        final org.neo4j.graphdb.Node neo4jNode = tx.createNode();
                        for (final String propertyKey : node.keySet())
                            setPropertySafe(node, neo4jNode, propertyKey);
                        nodeIdNeo4jIdMap.put(node.getId(), neo4jNode.getId());
                        neo4jNode.addLabel(Label.label(node.getLabel()));
                    }
                    tx.commit();
                }
            });
        }
        return nodeIdNeo4jIdMap;
    }

    private <T> void batchIterate(final Iterable<T> iterable, final Consumer<List<T>> consumer) {
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
                        node.getId() + "[:" + node.getLabel() + "]'");
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
        final String[] labels = graph.getEdgeLabels();
        for (int i = 0; i < labels.length; i++) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Creating edges with label '" + labels[i] + "' (" + (i + 1) + "/" + labels.length + ")...");
            batchIterate(graph.getEdges(labels[i]), edges -> {
                try (Transaction tx = dbService.beginTx()) {
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
                }
            });
        }
    }

    private void createNeo4jIndices(final Graph graph) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating indices...");
        final IndexDescription[] indices = graph.indexDescriptions();
        try (Transaction tx = dbService.beginTx()) {
            final Schema schema = tx.schema();
            for (final IndexDescription index : indices) {
                if (LOGGER.isInfoEnabled())
                    LOGGER.info("Creating " + index.getType() + " index on '" + index.getProperty() + "' field for " +
                                index.getTarget() + " label '" + index.getLabel() + "'...");
                final Label label = Label.label(index.getLabel());
                if (index.getTarget() == IndexDescription.Target.NODE) {
                    if (index.getType() == IndexDescription.Type.UNIQUE)
                        schema.constraintFor(label).assertPropertyIsUnique(index.getProperty()).create();
                    else
                        schema.indexFor(label).on(index.getProperty()).create();
                } else {
                    // TODO
                }
            }
            tx.commit();
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j indices", e);
        }
    }
}
