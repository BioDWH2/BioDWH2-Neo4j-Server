package de.unibi.agbi.biodwh2.neo4j.server;

import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.IndexDescription;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.TransactionTemplate;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.kernel.configuration.Settings;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

class Neo4jService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jService.class);

    private final String workspacePath;
    private final String neo4jPath;
    private final String databasePath;
    private final String importPath;
    private GraphDatabaseService dbService;
    private TransactionTemplate transactionTemplate;

    public Neo4jService(final String workspacePath) {
        this.workspacePath = workspacePath;
        neo4jPath = Paths.get(workspacePath, "neo4j").toString();
        databasePath = Paths.get(neo4jPath, "neo4j.db").toString();
        importPath = Paths.get(neo4jPath, "import").toString();
    }

    public void startNeo4jService(Integer port) {
        if (port == null)
            port = 8083;
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Starting Neo4j DBMS on bolt://localhost:" + port + "...");
        Paths.get(neo4jPath).toFile().mkdir();
        Paths.get(importPath).toFile().mkdir();
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
        builder.setConfig(GraphDatabaseSettings.procedure_unrestricted, "apoc.*");
        builder.setConfig(GraphDatabaseSettings.procedure_whitelist, "apoc.*");
        builder.setConfig(Settings.setting("dbms.directories.import", Settings.STRING, ""), importPath);
        builder.setConfig(Settings.setting("apoc.export.file.enabled", Settings.BOOLEAN, "false"), "true");
        dbService = builder.newGraphDatabase();
        registerApocProcedures(dbService);
        Runtime.getRuntime().addShutdownHook(new Thread(dbService::shutdown));
        transactionTemplate = new TransactionTemplate().with(dbService);
    }

    public static void registerApocProcedures(GraphDatabaseService db) {
        final Procedures proceduresService = ((GraphDatabaseAPI) db).getDependencyResolver().resolveDependency(
                Procedures.class, DependencyResolver.SelectionStrategy.ONLY);
        for (final Class<?> procedure : Factory.getInstance().loadAllClasses("apoc.")) {
            try {
                proceduresService.registerProcedure(procedure, false);
                proceduresService.registerFunction(procedure, false);
                proceduresService.registerAggregationFunction(procedure, false);
            } catch (Throwable ignored) {
            }
        }
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

    public void shutdown() {
        dbService.shutdown();
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
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating nodes...");
        batchIterate(graph.getNodes(), nodes -> transactionTemplate.execute(tx -> {
            for (final Node node : nodes) {
                final org.neo4j.graphdb.Node neo4jNode = dbService.createNode();
                for (final String propertyKey : node.keySet())
                    setPropertySafe(node, neo4jNode, propertyKey);
                nodeIdNeo4jIdMap.put(node.getId(), neo4jNode.getId());
                neo4jNode.addLabel(Label.label(node.getLabel()));
            }
        }));
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
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating edges...");
        batchIterate(graph.getEdges(), edges -> transactionTemplate.execute(tx -> {
            for (final Edge edge : edges) {
                final RelationshipType relationshipType = RelationshipType.withName(edge.getLabel());
                final org.neo4j.graphdb.Node fromNode = dbService.getNodeById(nodeIdNeo4jIdMap.get(edge.getFromId()));
                final org.neo4j.graphdb.Node toNode = dbService.getNodeById(nodeIdNeo4jIdMap.get(edge.getToId()));
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
        }));
    }

    private void createNeo4jIndices(final Graph graph) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Creating indices...");
        final IndexDescription[] indices = graph.indexDescriptions();
        try (Transaction tx = dbService.beginTx()) {
            Schema schema = dbService.schema();
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
            tx.success();
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to create neo4j indices", e);
        }
    }
}
