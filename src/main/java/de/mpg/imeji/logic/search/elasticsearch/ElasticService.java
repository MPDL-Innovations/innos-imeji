package de.mpg.imeji.logic.search.elasticsearch;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.log4j.Logger;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import de.mpg.imeji.presentation.util.PropertyReader;

/**
 * elasticsearch service for spot
 * 
 * @author bastiens
 * 
 */
public class ElasticService {
  private static Node node;
  public static Client client;
  private static String CLUSTER_NAME = "name of my cluster";
  private static boolean CLUSTER_LOCAL = true;
  private static boolean CLUSTER_DATA = true;
  private static String CLUSTER_DIR = "null";
  private static final Logger logger = Logger.getLogger(ElasticService.class);

  /**
   * The Index where all data are indexed
   */
  public static String DATA_ALIAS = "data";

  /**
   * The Types in Elasticsearch
   * 
   * @author bastiens
   * 
   */
  public enum ElasticTypes {
    items, folders, albums, spaces;
  }

  public static void start() throws IOException, URISyntaxException {
    CLUSTER_NAME = PropertyReader.getProperty("elastic.cluster.name");
    CLUSTER_DATA = Boolean.parseBoolean(PropertyReader.getProperty("elastic.cluster.data"));
    CLUSTER_LOCAL = Boolean.parseBoolean(PropertyReader.getProperty("elastic.cluster.local"));
    CLUSTER_DIR = PropertyReader.getProperty("elastic.cluster.home");
    logger.info("Connecting Node to " + CLUSTER_NAME + " (local=" + CLUSTER_LOCAL + ", data="
        + CLUSTER_DATA + ")");
    node =
        NodeBuilder.nodeBuilder().data(CLUSTER_DATA).local(CLUSTER_LOCAL).clusterName(CLUSTER_NAME)
            .settings(Settings.builder().put("path.home", CLUSTER_DIR)).node();

    client = node.client();
    new ElasticIndexer(DATA_ALIAS, ElasticTypes.items).addMapping();
    new ElasticIndexer(DATA_ALIAS, ElasticTypes.folders).addMapping();
    new ElasticIndexer(DATA_ALIAS, ElasticTypes.albums).addMapping();
    new ElasticIndexer(DATA_ALIAS, ElasticTypes.spaces).addMapping();
  }

  /**
   * Initialize the index. If index exists, don't create one, if not create one. Index is created
   * with DATA_ALIAS as alias
   * 
   * @return
   */
  public synchronized static String initializeIndex() {
    logger.info("Initializing ElasticSearch index.");
    String indexName = getIndexNameFromAliasName(DATA_ALIAS);
    if (indexName != null) {
      return indexName;
    } else {
      return createIndexWithAlias();
    }
  }

  /**
   * Get the First Index pointed by the Alias. This method should be used, when there is only one
   * Index pointed by the alias (on startup for instance)
   * 
   * @param aliasName
   * @return
   */
  public synchronized static String getIndexNameFromAliasName(final String aliasName) {
    ImmutableOpenMap<String, List<AliasMetaData>> map = client.admin().indices()
        .getAliases(new GetAliasesRequest(aliasName)).actionGet().getAliases();
    if (map.keys().size() > 1) {
      logger.error("Alias " + aliasName
          + " has more than one index. This is forbidden: All indexes will be removed, please reindex!!!");
      reset();
      return null;
    } else if (map.keys().size() == 1) {
      return map.keys().iterator().next().value;
    } else {
      return null;
    }
  }

  /**
   * Create an Index point an alias to it
   * 
   * @return
   */
  public static String createIndexWithAlias() {
    try {
      String indexName = createIndex();
      logger.info("Adding Alias to index " + indexName);
      ElasticService.client.admin().indices().prepareAliases().addAlias(indexName, DATA_ALIAS)
          .execute().actionGet();
      return indexName;
    } catch (Exception e) {
      logger.info("Index +" + "+ already existing");
    }
    return null;
  }

  /**
   * Create a new Index (without alias)
   * 
   * @return
   */
  public static String createIndex() {
    try {

      String indexName = DATA_ALIAS + "-" + System.currentTimeMillis();
      logger.info("Creating a new index " + indexName);
      ElasticService.client.admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
      return indexName;
    } catch (Exception e) {
      logger.info("Index +" + "+ already existing");
    }
    return null;
  }

  /**
   * Atomically move the alias from the old to the new index
   * 
   * @param oldIndex
   * @param newIndex
   */
  public static void setNewIndexAndRemoveOldIndex(String newIndex) {
    String oldIndex = getIndexNameFromAliasName(DATA_ALIAS);

    if (oldIndex != null && !oldIndex.equals(newIndex)) {
      ElasticService.client.admin().indices().prepareAliases().addAlias(newIndex, DATA_ALIAS)
          .removeAlias(oldIndex, DATA_ALIAS).execute().actionGet();
      ElasticService.client.admin().indices().prepareDelete(oldIndex).execute().actionGet();
    } else {
      ElasticService.client.admin().indices().prepareAliases().addAlias(newIndex, DATA_ALIAS)
          .execute().actionGet();
    }

  }

  /**
   * DANGER: delete all data from elasticsearch. A new reindex will be necessary
   */
  public static void reset() {
    logger.warn("Resetting ElasticSearch!!!");
    logger.warn("Deleting all indexes...");
    ElasticService.client.admin().indices().prepareDelete(DATA_ALIAS).execute().actionGet();
    logger.warn("...done!");
    initializeIndex();
  }

  public static void shutdown() {
    node.close();
  }
}