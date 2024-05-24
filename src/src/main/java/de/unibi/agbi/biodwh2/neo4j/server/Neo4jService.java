package de.unibi.agbi.biodwh2.neo4j.server;

import de.unibi.agbi.biodwh2.core.collections.BatchIterable;
import de.unibi.agbi.biodwh2.core.collections.Tuple2;
import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreCollection;
import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreModel;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.IndexDescription;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.neo4j.graphdb.schema.IndexType;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

class Neo4jService {
    private static final Logger LOGGER = LogManager.getLogger(Neo4jService.class);
    private static final Setting<Boolean> bolt_ssl_policy = SettingImpl.newBuilder("dbms.ssl.policy.bolt.enabled",
                                                                                   SettingValueParsers.BOOL, false)
                                                                       .build();

    private final String workspacePath;
    private final String neo4jPath;
    private final Path databasePath;
    private final String importPath;
    private DatabaseManagementService managementService;
    private GraphDatabaseService dbService;
    private Method getOrCreateNodeRepository;
    private Method getOrCreateEdgeRepository;

    public Neo4jService(final String workspacePath) {
        this.workspacePath = workspacePath;
        neo4jPath = Paths.get(workspacePath, "neo4j").toString();
        databasePath = Paths.get(neo4jPath, "neo4j.db");
        importPath = Paths.get(neo4jPath, "import").toString();

        try {
            getOrCreateNodeRepository = Graph.class.getSuperclass().getDeclaredMethod("getOrCreateNodeRepository",
                                                                                      String.class);
            getOrCreateNodeRepository.setAccessible(true);
            getOrCreateEdgeRepository = Graph.class.getSuperclass().getDeclaredMethod("getOrCreateEdgeRepository",
                                                                                      String.class);
            getOrCreateEdgeRepository.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            getOrCreateNodeRepository = null;
            getOrCreateEdgeRepository = null;
        }
    }

    public void startNeo4jService(final Integer boltPort) {
        Paths.get(neo4jPath).toFile().mkdir();
        Paths.get(importPath).toFile().mkdir();
        var boltListenAddress = new SocketAddress("0.0.0.0", boltPort == null ? 8083 : boltPort);
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Starting Neo4j DBMS on bolt://{}...", boltListenAddress);
        final var builder = new DatabaseManagementServiceBuilder(databasePath);
        builder.setConfig(GraphDatabaseSettings.pagecache_memory, 512 * 1024L);
        builder.setConfig(HttpConnector.enabled, false);
        builder.setConfig(HttpsConnector.enabled, false);
        builder.setConfig(BoltConnector.enabled, true);
        builder.setConfig(BoltConnector.listen_address, boltListenAddress);
        builder.setConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED);
        builder.setConfig(bolt_ssl_policy, false);
        builder.setConfig(GraphDatabaseSettings.auth_enabled, false);
        builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, Collections.singletonList("apoc.*"));
        builder.setConfig(GraphDatabaseSettings.procedure_allowlist, Collections.singletonList("apoc.*"));
        // builder.set(GraphDatabaseSettings.store_internal_log_level, Level.DEBUG);
        // builder.set(SettingImpl.newBuilder("dbms.directories.import", SettingValueParsers.PATH, null).build(), Paths.get(importPath));
        // builder.set(SettingImpl.newBuilder("apoc.import.file.enabled", SettingValueParsers.BOOL, false).build(), true);
        // builder.set(SettingImpl.newBuilder("apoc.export.file.enabled", SettingValueParsers.BOOL, false).build(), true);
        managementService = builder.build();
        dbService = managementService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
        Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
        registerApocProceduresAndFunctions();
    }

    private void registerApocProceduresAndFunctions() {
        final GlobalProcedures procedures = ((GraphDatabaseAPI) dbService).getDependencyResolver().resolveDependency(
                GlobalProcedures.class);
        final List<Class<?>> apocClasses = Factory.getInstance().loadAllClasses("apoc.");
        apocClasses.forEach((proc) -> {
            try {
                procedures.registerProcedure(proc);
            } catch (Throwable ignored) {
            }
            try {
                procedures.registerFunction(proc);
            } catch (Throwable ignored) {
            }
            try {
                procedures.registerAggregationFunction(proc);
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
                LOGGER.error("Failed to remove old database '{}'", neo4jPath, e);
        }
    }

    public void shutdown() {
        managementService.shutdown();
    }

    public void createDatabase() {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating Neo4j database...");
        try (Graph graph = new Graph(Paths.get(workspacePath, "sources/mapped.db"), true, true)) {
            final HashMap<Long, String> nodeIdNeo4jIdMap = createNeo4jNodes(graph);
            createNeo4jEdges(graph, nodeIdNeo4jIdMap);
            createNeo4jIndices(graph);
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j database '{}'", databasePath, e);
        }
    }

    private HashMap<Long, String> createNeo4jNodes(final Graph graph) {
        final HashMap<Long, String> nodeIdNeo4jIdMap = new HashMap<>();
        final String[] labels = graph.getNodeLabels();
        for (int i = 0; i < labels.length; i++) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Creating nodes with label '{}' ({}/{})...", labels[i], i + 1, labels.length);
            final var nodes = getNodes(graph, labels[i]);
            while (nodes.getFirst().hasNext()) {
                try (Transaction tx = dbService.beginTx()) {
                    for (final Node node : nodes.getSecond()) {
                        final org.neo4j.graphdb.Node neo4jNode = tx.createNode();
                        for (final String propertyKey : node.keySet())
                            setPropertySafe(node, neo4jNode, propertyKey);
                        nodeIdNeo4jIdMap.put(node.getId(), neo4jNode.getElementId());
                        neo4jNode.addLabel(Label.label(node.getLabel()));
                    }
                    tx.commit();
                }
            }
        }
        return nodeIdNeo4jIdMap;
    }

    private Tuple2<Iterator<Node>, BatchIterable<Node>> getNodes(final Graph graph, final String label) {
        if (getOrCreateNodeRepository != null) {
            try {
                //noinspection unchecked
                final var collection = (MVStoreCollection<Node>) getOrCreateNodeRepository.invoke(graph, label);
                final var iterator = collection.unsafeIterator();
                return new Tuple2<>(iterator, new BatchIterable<>(iterator));
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        final var iterator = graph.getNodes(label).iterator();
        return new Tuple2<>(iterator, new BatchIterable<>(iterator));
    }

    private Tuple2<Iterator<Edge>, BatchIterable<Edge>> getEdges(final Graph graph, final String label) {
        if (getOrCreateEdgeRepository != null) {
            try {
                //noinspection unchecked
                final var collection = (MVStoreCollection<Edge>) getOrCreateEdgeRepository.invoke(graph, label);
                final var iterator = collection.unsafeIterator();
                return new Tuple2<>(iterator, new BatchIterable<>(iterator));
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        final var iterator = graph.getEdges(label).iterator();
        return new Tuple2<>(iterator, new BatchIterable<>(iterator));
    }

    private void setPropertySafe(final Node node, final org.neo4j.graphdb.Node neo4jNode, final String propertyKey) {
        try {
            if (MVStoreModel.ID_FIELD.equals(propertyKey) || !Node.IGNORED_FIELDS.contains(propertyKey)) {
                Object value = node.getProperty(propertyKey);
                if (value instanceof Integer[] array)
                    for (int i = 0; i < array.length; i++)
                        array[i] = array[i] == null ? -1 : array[i];
                if (value instanceof Long[] array)
                    for (int i = 0; i < array.length; i++)
                        array[i] = array[i] == null ? -1 : array[i];
                if (value instanceof Collection)
                    value = convertCollectionToArray((Collection<?>) value);
                if (value instanceof Enum<?>)
                    value = value.toString();
                if (value != null)
                    neo4jNode.setProperty(propertyKey, value);
            }
        } catch (IllegalArgumentException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Illegal property '{}' -> '{}' for node '{}[:{}]'", propertyKey,
                            node.getProperty(propertyKey), node.getId(), node.getLabel(), e);
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

    private void createNeo4jEdges(final Graph graph, final HashMap<Long, String> nodeIdNeo4jIdMap) {
        final String[] labels = graph.getEdgeLabels();
        for (int i = 0; i < labels.length; i++) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Creating edges with label '{}' ({}/{})...", labels[i], i + 1, labels.length);
            final long edgeCount = graph.getNumberOfEdges(labels[i]);
            final long[] counter = {0};
            final var edges = getEdges(graph, labels[i]);
            while (edges.getFirst().hasNext()) {
                try (final Transaction tx = dbService.beginTx()) {
                    for (final Edge edge : edges.getSecond()) {
                        final RelationshipType relationshipType = RelationshipType.withName(edge.getLabel());
                        final var fromNode = tx.getNodeByElementId(nodeIdNeo4jIdMap.get(edge.getFromId()));
                        final var toNode = tx.getNodeByElementId(nodeIdNeo4jIdMap.get(edge.getToId()));
                        final Relationship relationship = fromNode.createRelationshipTo(toNode, relationshipType);
                        for (final String propertyKey : edge.keySet())
                            if (MVStoreModel.ID_FIELD.equals(propertyKey) || !Edge.IGNORED_FIELDS.contains(
                                    propertyKey)) {
                                Object value = edge.getProperty(propertyKey);
                                if (value instanceof Collection)
                                    value = convertCollectionToArray((Collection<?>) value);
                                if (value != null)
                                    relationship.setProperty(propertyKey, value);
                            }
                        counter[0]++;
                        if (counter[0] % 100_000 == 0)
                            LOGGER.info("\tProgress: {}/{}...", counter[0], edgeCount);
                    }
                    tx.commit();
                }
            }
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
                    LOGGER.info("Creating {} index on '{}' field for {} label '{}'...", index.getType(),
                                index.getProperty(), index.getTarget(), index.getLabel());
                final Label label = Label.label(index.getLabel());
                if (index.getTarget() == IndexDescription.Target.NODE) {
                    if (index.getType() == IndexDescription.Type.UNIQUE)
                        schema.constraintFor(label).withIndexType(IndexType.RANGE).assertPropertyIsUnique(
                                index.getProperty()).create();
                    else
                        schema.indexFor(label).withIndexType(IndexType.RANGE).on(index.getProperty()).create();
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
