package de.mpg.imeji.logic.search.elasticsearch.factory;


import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.hp.hpl.jena.util.iterator.Filter;

import de.mpg.imeji.logic.authorization.util.SecurityUtil;
import de.mpg.imeji.logic.config.ImejiLicenses;
import de.mpg.imeji.logic.search.elasticsearch.ElasticService;
import de.mpg.imeji.logic.search.elasticsearch.ElasticService.ElasticTypes;
import de.mpg.imeji.logic.search.elasticsearch.factory.util.ElasticSearchFactoryUtil;
import de.mpg.imeji.logic.search.elasticsearch.model.ElasticFields;
import de.mpg.imeji.logic.search.model.SearchElement;
import de.mpg.imeji.logic.search.model.SearchGroup;
import de.mpg.imeji.logic.search.model.SearchIndex.SearchFields;
import de.mpg.imeji.logic.search.model.SearchLogicalRelation;
import de.mpg.imeji.logic.search.model.SearchLogicalRelation.LOGICAL_RELATIONS;
import de.mpg.imeji.logic.search.model.SearchMetadata;
import de.mpg.imeji.logic.search.model.SearchOperators;
import de.mpg.imeji.logic.search.model.SearchPair;
import de.mpg.imeji.logic.search.model.SearchQuery;
import de.mpg.imeji.logic.search.model.SearchSimpleMetadata;
import de.mpg.imeji.logic.search.model.SearchTechnicalMetadata;
import de.mpg.imeji.logic.search.util.SearchUtils;
import de.mpg.imeji.logic.util.DateFormatter;
import de.mpg.imeji.logic.util.ObjectHelper;
import de.mpg.imeji.logic.vo.Grant;
import de.mpg.imeji.logic.vo.Grant.GrantType;
import de.mpg.imeji.logic.vo.Properties.Status;
import de.mpg.imeji.logic.vo.User;

/**
 * Factory to create an ElasticSearch query from the {@link SearchQuery}
 *
 * @author bastiens
 *
 */
public class ElasticQueryFactory {
  private static final Logger LOGGER = Logger.getLogger(ElasticQueryFactory.class);
  private static final float FULLTEXT_MIN_SCORE = 0.5f;

  /**
   * Build a {@link QueryBuilder} from a {@link SearchQuery}
   *
   * @param query
   * @return
   * @return
   */
  public static QueryBuilder build(SearchQuery query, String folderUri, String spaceId, User user,
      ElasticTypes type) {
    final BoolQueryBuilder q = QueryBuilders.boolQuery();
    final QueryBuilder searchQuery = buildSearchQuery(query, user);
    final QueryBuilder containerQuery = buildContainerFilter(folderUri);
    final QueryBuilder securityQuery = buildSecurityQuery(user, folderUri);
    final QueryBuilder spaceQuery = buildSpaceQuery(spaceId);
    final QueryBuilder statusQuery = buildStatusQuery(query, user);
    if (!isMatchAll(searchQuery)) {
      q.must(searchQuery);
    }
    if (!isMatchAll(containerQuery)) {
      q.must(containerQuery);
    }
    if (!isMatchAll(securityQuery)) {
      q.must(securityQuery);
    }
    if (!isMatchAll(spaceQuery)) {
      q.must(spaceQuery);
    }
    if (type != ElasticTypes.users && !isMatchAll(statusQuery)) {
      q.must(statusQuery);
    }
    return q;
  }

  /**
   * True if the query is a match all query
   *
   * @param q
   * @return
   */
  private static boolean isMatchAll(QueryBuilder q) {
    return q instanceof MatchAllQueryBuilder;
  }

  /**
   * The {@link QueryBuilder} with the search query
   *
   * @param query
   * @return
   */
  private static QueryBuilder buildSearchQuery(SearchQuery query, User user) {
    if (query == null || query.getElements().isEmpty()) {
      return QueryBuilders.matchAllQuery();
    }
    return buildSearchQuery(query.getElements(), user);
  }

  /**
   * Build a query for the status
   *
   * @param query
   * @param user
   * @return
   */
  private static QueryBuilder buildStatusQuery(SearchQuery query, User user) {
    if (user == null) {
      // Not Logged in: can only view release objects
      return fieldQuery(ElasticFields.STATUS, Status.RELEASED.name(), SearchOperators.EQUALS,
          false);
    } else if (query != null && hasStatusQuery(query.getElements())) {
      // Don't filter, since it is done later via the searchquery
      return QueryBuilders.matchAllQuery();
    } else {
      // Default = don't view discarded objects
      return fieldQuery(ElasticFields.STATUS, Status.WITHDRAWN.name(), SearchOperators.EQUALS,
          true);
    }

  }

  /**
   * Check if at least on {@link SearchPair} is related to the status. If yes, return true
   *
   * @param elements
   * @return
   */
  private static boolean hasStatusQuery(List<SearchElement> elements) {
    for (final SearchElement e : elements) {
      if (e instanceof SearchPair && ((SearchPair) e).getField() == SearchFields.status) {
        return true;
      } else if (e instanceof SearchGroup && hasStatusQuery(e.getElements())) {
        return true;
      }
    }
    return false;
  }


  /**
   * Return the query for space
   *
   * @param spaceId
   * @return
   */
  private static QueryBuilder buildSpaceQuery(String spaceId) {
    // TODO create query lookup query (see by albums)
    if (spaceId == null || "".equals(spaceId)) {
      return QueryBuilders.matchAllQuery();
    } else {
      return fieldQuery(ElasticFields.SPACE, spaceId, SearchOperators.EQUALS, false);
    }
  }

  /**
   * Build a {@link QueryBuilder} from a list of {@link SearchElement}
   *
   * @param elements
   * @return
   */
  private static QueryBuilder buildSearchQuery(List<SearchElement> elements, User user) {
    boolean OR = true;
    final BoolQueryBuilder q = QueryBuilders.boolQuery();
    for (final SearchElement el : elements) {
      if (el instanceof SearchPair) {
        if (OR) {
          q.should(termQuery((SearchPair) el, user));
        } else {
          q.must(termQuery((SearchPair) el, user));
        }
      } else if (el instanceof SearchLogicalRelation) {
        OR = ((SearchLogicalRelation) el).getLogicalRelation() == LOGICAL_RELATIONS.OR ? true
            : false;
      } else if (el instanceof SearchGroup) {
        if (OR) {
          q.should(buildSearchQuery(((SearchGroup) el).getElements(), user));
        } else {
          q.must(buildSearchQuery(((SearchGroup) el).getElements(), user));
        }
      }
    }
    return q;
  }

  /**
   * Build the security Query according to the user.
   *
   * @param user
   * @return
   */
  private static QueryBuilder buildSecurityQuery(User user, String folderUri) {
    if (user != null) {
      if (SecurityUtil.authorization().isSysAdmin(user)) {
        // Admin: can view everything
        return QueryBuilders.matchAllQuery();
      } else {
        // normal user
        return buildGrantQuery(user, null);
      }
    }
    return QueryBuilders.matchAllQuery();
  }

  /**
   * Build a Filter for a container (album or folder): if the containerUri is not null, search
   * result will be filter to this only container
   *
   * @param containerUri
   * @return
   */
  private static QueryBuilder buildContainerFilter(String containerUri) {
    if (containerUri != null) {
      if (isFolderUri(containerUri)) {
        return fieldQuery(ElasticFields.FOLDER, containerUri, SearchOperators.EQUALS, false);
      } else {
        return QueryBuilders.termsLookupQuery(ElasticFields.ID.field())
            .lookupIndex(ElasticService.DATA_ALIAS).lookupId(containerUri)
            .lookupType(ElasticTypes.albums.name()).lookupPath(ElasticFields.MEMBER.field());
      }
    }
    return QueryBuilders.matchAllQuery();
  }


  /**
   * Build the query with all Read grants
   *
   * @param grants
   * @return
   */
  private static QueryBuilder buildGrantQuery(User user, GrantType grantType) {
    Collection<Grant> grants =
        SecurityUtil.authorization().toGrantList(SecurityUtil.authorization().getAllGrants(user));
    final BoolQueryBuilder q = QueryBuilders.boolQuery();
    // Add query for all release objects
    if (grantType == null) {
      q.should(
          fieldQuery(ElasticFields.STATUS, Status.RELEASED.name(), SearchOperators.EQUALS, false));
    }
    // if grantType is null, set it to READ
    grantType = grantType == null ? GrantType.READ : grantType;
    // Add query for each read grant
    for (final Grant g : grants) {
      if (g.asGrantType() == grantType) {
        q.should(fieldQuery(ElasticFields.FOLDER, g.getGrantFor().toString(),
            SearchOperators.EQUALS, false));
        q.should(fieldQuery(ElasticFields.ID, g.getGrantFor().toString(), SearchOperators.EQUALS,
            false));
      }
    }
    return q;
  }



  /**
   * Create a QueryBuilder with a term filter (see
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-term-filter.html)
   *
   * @param pair
   * @return
   */
  private static QueryBuilder termQuery(SearchPair pair, User user) {
    if (pair instanceof SearchMetadata) {
      return metadataFilter((SearchMetadata) pair);
    } else if (pair instanceof SearchTechnicalMetadata) {
      return technicalMetadataQuery((SearchTechnicalMetadata) pair);
    } else if (pair instanceof SearchSimpleMetadata) {
      simpleMetadataQuery((SearchSimpleMetadata) pair);
    }
    final SearchFields index = pair.getField();
    switch (index) {
      case alb:
        break;
      case all:
        final BoolQueryBuilder f = QueryBuilders.boolQuery()
            .should(fieldQuery(ElasticFields.ALL, pair.getValue(), SearchOperators.REGEX, false));
        if (NumberUtils.isNumber(pair.getValue())) {
          f.should(fieldQuery(ElasticFields.METADATA_NUMBER, pair.getValue(),
              SearchOperators.EQUALS, false));
        }
        return negate(f, pair.isNot());
      case fulltext:
        return contentQuery(
            fieldQuery(ElasticFields.FULLTEXT, pair.getValue(), pair.getOperator(), pair.isNot()));
      case checksum:
        return fieldQuery(ElasticFields.CHECKSUM, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case citation:
        return fieldQuery(ElasticFields.METADATA_TEXT, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case col:
        return fieldQuery(ElasticFields.FOLDER, pair.getValue(), pair.getOperator(), pair.isNot());
      case cone:
        // not indexed
        break;
      case description:
        return fieldQuery(ElasticFields.DESCRIPTION, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case cont_md:
        // not indexed
        break;
      case cont_person:
        // not indexed
        break;
      case author_familyname:
        // not indexed
        break;
      case author_givenname:
        // not indexed
        break;
      case author_name:
        // not indexed
        break;
      case cont_person_org:
        return fieldQuery(ElasticFields.AUTHOR_ORGANIZATION_NAME, pair.getValue(),
            pair.getOperator(), pair.isNot());
      case author_org_name:
        // not indexed
        break;
      case title:
        // not indexed
        break;
      case created:
        return timeQuery(ElasticFields.CREATED, pair.getValue(), pair.getOperator(), pair.isNot());
      case creator_id:
        // not indexed
        break;
      case creator:
        return fieldQuery(ElasticFields.CREATOR,
            ElasticSearchFactoryUtil.getUserId(pair.getValue()), pair.getOperator(), pair.isNot());
      case collaborator:
        return roleQueryWithoutCreator(ElasticFields.READ, pair.getValue(), pair.isNot());
      case uploader:
        return roleQuery(ElasticFields.UPLOAD, pair.getValue(), pair.isNot());
      case read:
        return fieldQuery(ElasticFields.READ, pair.getValue(), SearchOperators.EQUALS,
            pair.isNot());
      case date:
        return timeQuery(ElasticFields.METADATA_NUMBER, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case editor:
        // not indexed
        break;
      case filename:
        return fieldQuery(ElasticFields.NAME, pair.getValue(), pair.getOperator(), pair.isNot());
      case filetype:
        final BoolQueryBuilder filetypeQuery = QueryBuilders.boolQuery();
        for (final String ext : SearchUtils.parseFileTypesAsExtensionList(pair.getValue())) {
          filetypeQuery.should(
              fieldQuery(ElasticFields.NAME, "\"." + ext + "\"", SearchOperators.REGEX, false));
        }
        return filetypeQuery;
      case grant:
        // same as grant_type
        final GrantType grant = pair.getValue().equals("upload") ? GrantType.EDIT
            : GrantType.valueOf(pair.getValue().toUpperCase());
        return buildGrantQuery(user, grant);
      case grant_for:
        // not indexed
        break;
      case grant_type:
        // same as grant
        final GrantType grantType = pair.getValue().equals("upload") ? GrantType.EDIT
            : GrantType.valueOf(pair.getValue().toUpperCase());
        return buildGrantQuery(user, grantType);
      case member:
        return fieldQuery(ElasticFields.MEMBER, pair.getValue(), pair.getOperator(), pair.isNot());
      case label:
        // not indexed
        break;
      case license:
        final BoolQueryBuilder licenseQuery = QueryBuilders.boolQuery();
        for (final String licenseName : pair.getValue().split(" OR ")) {
          if (ImejiLicenses.NO_LICENSE.equals(licenseName)) {
            licenseQuery.should(QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.existsQuery(ElasticFields.LICENSE.field())));
          } else if ("*".equals(licenseName)) {
            licenseQuery.should(QueryBuilders.existsQuery(ElasticFields.LICENSE.field()));
          } else {
            licenseQuery.should(
                fieldQuery(ElasticFields.LICENSE, licenseName, SearchOperators.REGEX, false));
          }
        }
        return licenseQuery;
      case md:
        // not indexed
        break;
      case mds:
        // not indexed
        break;
      case modified:
        return timeQuery(ElasticFields.MODIFIED, pair.getValue(), pair.getOperator(), pair.isNot());
      case number:
        return fieldQuery(ElasticFields.METADATA_NUMBER, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case person:
        // not indexed
        break;
      case person_family:
        return fieldQuery(ElasticFields.METADATA_FAMILYNAME, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case person_given:
        return fieldQuery(ElasticFields.METADATA_GIVENNAME, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case person_id:
        // not indexed
        break;
      case person_completename:
        // not indexed
        break;
      case person_org:
        // not indexed
        break;
      case person_org_city:
        // not indexed
        break;
      case person_org_country:
        // not indexed
        break;
      case person_org_description:
        // not indexed
        break;
      case person_org_id:
        // not indexed
        break;
      case person_org_name:
        // not indexed
        break;
      case person_role:
        // not indexed
        break;
      case prof:
        return fieldQuery(ElasticFields.PROFILE, pair.getValue(), pair.getOperator(), pair.isNot());
      case prop:
        // not indexed
        break;
      case statement:
        final String statementID = ObjectHelper.getId(URI.create(pair.getValue()));
        return fieldQuery(ElasticFields.METADATA_STATEMENT, statementID, pair.getOperator(),
            pair.isNot());
      case status:
        // transform http://imeji.org/terms/status#RELEASED into RELEASED
        String status = pair.getValue();
        if (status.contains("#")) {
          status = status.split("#")[1];
        }
        if ("private".equals(status)) {
          status = Status.PENDING.name();
        }
        if ("public".equals(status)) {
          status = Status.RELEASED.name();
        }
        if ("discarded".equals(status)) {
          status = Status.WITHDRAWN.name();
        }
        return fieldQuery(ElasticFields.STATUS, status.toUpperCase(), pair.getOperator(),
            pair.isNot());
      case text:
        return fieldQuery(ElasticFields.METADATA_TEXT, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case time:
        return timeQuery(ElasticFields.METADATA_NUMBER, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case location:
        // not indexed
        break;
      case metadatatype:
        return fieldQuery(ElasticFields.METADATA_TYPE, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case url:
        return fieldQuery(ElasticFields.METADATA_URI, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case hasgrant:
        // TODO: with current indexed data, it is only possible to search for the creator. Grants
        // should be indexed to get all objects where the user has grant for
        return fieldQuery(ElasticFields.CREATOR, pair.getValue(), pair.getOperator(), pair.isNot());
      case visibility:
        // not indexed
        break;
      case coordinates:
        return fieldQuery(ElasticFields.METADATA_LOCATION, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case pid:
        return fieldQuery(ElasticFields.PID, pair.getValue(), pair.getOperator(), pair.isNot());
      case info_label:
        return fieldQuery(ElasticFields.INFO_LABEL, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case info_text:
        return fieldQuery(ElasticFields.INFO_TEXT, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case info_url:
        return fieldQuery(ElasticFields.INFO_URL, pair.getValue(), pair.getOperator(),
            pair.isNot());
      case email:
        return fieldQuery(ElasticFields.EMAIL, pair.getValue(), SearchOperators.REGEX,
            pair.isNot());
      default:
        break;
    }
    return matchNothing();
  }

  /**
   * Create a {@link QueryBuilder} for a {@link SearchMetadata}
   *
   * @param md
   * @return
   */
  private static QueryBuilder metadataFilter(SearchMetadata md) {
    switch (md.getField()) {
      case text:
        return metadataQuery(
            fieldQuery(ElasticFields.METADATA_TEXT, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
      case citation:
        return metadataQuery(
            fieldQuery(ElasticFields.METADATA_TEXT, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
      case number:
        return metadataQuery(
            fieldQuery(ElasticFields.METADATA_NUMBER, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
      case date:
        return metadataQuery(
            fieldQuery(ElasticFields.METADATA_TEXT, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
      case url:
        return metadataQuery(
            fieldQuery(ElasticFields.METADATA_URI, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
      case person_family:
        return metadataQuery(fieldQuery(ElasticFields.METADATA_FAMILYNAME, md.getValue(),
            md.getOperator(), md.isNot()), md.getStatement());
      case person_given:
        return metadataQuery(fieldQuery(ElasticFields.METADATA_GIVENNAME, md.getValue(),
            md.getOperator(), md.isNot()), md.getStatement());
      case coordinates:
        return metadataQuery(fieldQuery(ElasticFields.METADATA_LOCATION, md.getValue(),
            md.getOperator(), md.isNot()), md.getStatement());
      case time:
        return metadataQuery(
            timeQuery(ElasticFields.METADATA_NUMBER, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
      default:
        return metadataQuery(
            fieldQuery(ElasticFields.METADATA_TEXT, md.getValue(), md.getOperator(), md.isNot()),
            md.getStatement());
    }
  }

  /**
   * Create a {@link QueryBuilder}
   *
   * @param index
   * @param value
   * @param operator
   * @return
   */
  private static QueryBuilder fieldQuery(ElasticFields field, String value,
      SearchOperators operator, boolean not) {
    QueryBuilder q = null;

    if (operator == null) {
      operator = SearchOperators.REGEX;
    }
    switch (operator) {
      case REGEX:
        q = matchFieldQuery(field, ElasticSearchFactoryUtil.escape(value));
        break;
      case EQUALS:
        q = exactFieldQuery(field, value);
        break;
      case GREATER:
        q = greaterThanQuery(field, value);
        break;
      case LESSER:
        q = lessThanQuery(field, value);
        break;
      case GEO:
        q = geoQuery(value);
        break;
      default:
        // default is REGEX
        q = matchFieldQuery(field, value);
        break;
    }
    return negate(q, not);
  }

  /**
   * Search for a date saved as a time (i.e) in ElasticSearch
   *
   * @param field
   * @param dateString
   * @param operator
   * @param not
   * @return
   */
  private static QueryBuilder timeQuery(ElasticFields field, String dateString,
      SearchOperators operator, boolean not) {
    QueryBuilder q = null;
    if (operator == null) {
      operator = SearchOperators.REGEX;
    }
    switch (operator) {
      case GREATER:
        q = greaterThanQuery(field, Long.toString(DateFormatter.getTime(dateString)));
        break;
      case LESSER:
        q = lessThanQuery(field, Long.toString(DateFormatter.getTime(dateString)));
        break;
      default:
        q = QueryBuilders.rangeQuery(field.field())
            .gte(Long.toString(DateFormatter.parseDate(dateString).getTime()))
            .lte(Long.toString(DateFormatter.parseDate2(dateString).getTime()));
        break;
    }
    return negate(q, not);
  }



  /**
   * Create a {@link QueryBuilder} - used to sarch for metadata which are defined with a statement
   *
   * @param index
   * @param value
   * @param operator
   * @param statement
   * @return
   */
  private static QueryBuilder metadataQuery(QueryBuilder valueQuery, URI statement) {
    return QueryBuilders.nestedQuery(ElasticFields.METADATA.field(),
        QueryBuilders.boolQuery().must(valueQuery).must(fieldQuery(ElasticFields.METADATA_STATEMENT,
            ObjectHelper.getId(statement), SearchOperators.EQUALS, false)));

  }

  /**
   * Query for technical metadata
   *
   * @param label
   * @param value
   * @param not
   * @return
   */
  private static QueryBuilder technicalMetadataQuery(SearchTechnicalMetadata tmd) {
    return contentQuery(QueryBuilders.nestedQuery(ElasticFields.TECHNICAL.field(),
        QueryBuilders.boolQuery()
            .must(fieldQuery(ElasticFields.TECHNICAL_NAME, tmd.getLabel(), SearchOperators.EQUALS,
                false))
            .must(fieldQuery(ElasticFields.TECHNICAL_VALUE, tmd.getValue(), tmd.getOperator(),
                tmd.isNot()))));
  }

  /**
   * Query for metadata via metadata label (label="value")
   *
   * @param label
   * @param value
   * @param not
   * @return
   */
  private static QueryBuilder simpleMetadataQuery(SearchSimpleMetadata smd) {
    return QueryBuilders.boolQuery()
        .must(
            fieldQuery(ElasticFields.TECHNICAL_NAME, smd.getLabel(), SearchOperators.EQUALS, false))
        .must(fieldQuery(ElasticFields.TECHNICAL_VALUE, smd.getValue(), smd.getOperator(),
            smd.isNot()));
  }

  /**
   * Search for the exact value of a field
   *
   * @param field
   * @param value
   * @return
   */
  private static QueryBuilder exactFieldQuery(ElasticFields field, String value) {
    return QueryBuilders.termQuery(field.fieldExact(), value);
  }

  /**
   * Search for a match (not the exact value)
   *
   * @param field
   * @param value
   * @return
   */
  private static QueryBuilder matchFieldQuery(ElasticFields field, String value) {
    if (field == ElasticFields.ALL) {
      return QueryBuilders
          .queryStringQuery(value + " " + ElasticFields.NAME.field() + ".suggest:" + value);
    }
    return QueryBuilders.queryStringQuery(field.field() + ":" + value);
  }

  /**
   * Search for value greater than the searched value
   *
   * @param field
   * @param value
   * @return
   */
  private static QueryBuilder greaterThanQuery(ElasticFields field, String value) {
    if (NumberUtils.isNumber(value)) {
      return QueryBuilders.rangeQuery(field.field()).gte(Double.parseDouble(value));
    }
    return matchNothing();
  }

  /**
   * Search for value smaller than searched value
   *
   * @param field
   * @param value
   * @return
   */
  private static QueryBuilder lessThanQuery(ElasticFields field, String value) {
    if (NumberUtils.isNumber(value)) {
      return QueryBuilders.rangeQuery(field.field()).lte(Double.parseDouble(value));
    }
    return matchNothing();
  }

  private static QueryBuilder geoQuery(String value) {
    final String[] values = value.split(",");
    String distance = "1km";
    final double lat = Double.parseDouble(values[0]);
    final double lon = Double.parseDouble(values[1]);
    if (values.length == 3) {
      distance = values[2];
    }
    return QueryBuilders.geoDistanceQuery(ElasticFields.METADATA_LOCATION.field())
        .distance(distance).point(lat, lon);
  }

  /**
   * Add NOT filter to the {@link Filter} if not is true
   *
   * @param f
   * @param not
   * @return
   */
  private static QueryBuilder negate(QueryBuilder f, boolean not) {
    return not ? QueryBuilders.notQuery(f) : f;
  }

  /**
   * Return a query which find nothing
   *
   * @return
   */
  private static QueryBuilder matchNothing() {
    return QueryBuilders.notQuery(QueryBuilders.matchAllQuery());
  }

  /**
   * True if the uri is an uri folder
   *
   * @param uri
   * @return
   */
  private static boolean isFolderUri(String uri) {
    return uri.contains("/collection/") ? true : false;
  }

  /**
   * Create the query for role="email". Role can be uploader, collaborator. Objects where the user
   * is creator are not excluded
   *
   * @param email
   * @param not
   * @return
   */
  private static QueryBuilder roleQuery(ElasticFields role, String email, boolean not) {
    final String userId = ElasticSearchFactoryUtil.getUserId(email);
    final QueryBuilder q1 = QueryBuilders.termsLookupQuery(ElasticFields.ID.field())
        .lookupIndex(ElasticService.DATA_ALIAS).lookupId(userId)
        .lookupType(ElasticTypes.users.name()).lookupPath(role.field());
    final QueryBuilder q2 = QueryBuilders.termsLookupQuery(ElasticFields.FOLDER.field())
        .lookupIndex(ElasticService.DATA_ALIAS).lookupId(userId)
        .lookupType(ElasticTypes.users.name()).lookupPath(role.field());
    final BoolQueryBuilder q = QueryBuilders.boolQuery().should(q1).should(q2);
    final List<String> groups = ElasticSearchFactoryUtil.getGroupsOfUser(userId);
    for (final String group : groups) {
      q.should(QueryBuilders.termsLookupQuery(ElasticFields.ID.field())
          .lookupIndex(ElasticService.DATA_ALIAS).lookupId(group)
          .lookupType(ElasticTypes.usergroups.name()).lookupPath(role.field()));
    }
    return q;
  }



  /**
   * Create the query for role="email". Role can be uploader, collaborator. Objects where the user
   * is creator will be excluded
   *
   * @param email
   * @param not
   * @return
   */
  private static QueryBuilder roleQueryWithoutCreator(ElasticFields role, String email,
      boolean not) {
    final String userId = ElasticSearchFactoryUtil.getUserId(email);
    final BoolQueryBuilder q1 = QueryBuilders.boolQuery();
    q1.must(QueryBuilders.termsLookupQuery(ElasticFields.ID.field())
        .lookupIndex(ElasticService.DATA_ALIAS).lookupId(userId)
        .lookupType(ElasticTypes.users.name()).lookupPath(role.field()));
    q1.mustNot(fieldQuery(ElasticFields.CREATOR, ElasticSearchFactoryUtil.getUserId(email),
        SearchOperators.EQUALS, not));
    final BoolQueryBuilder q2 = QueryBuilders.boolQuery();
    q2.must(QueryBuilders.termsLookupQuery(ElasticFields.FOLDER.field())
        .lookupIndex(ElasticService.DATA_ALIAS).lookupId(userId)
        .lookupType(ElasticTypes.users.name()).lookupPath(role.field()));
    q2.mustNot(fieldQuery(ElasticFields.CREATOR, ElasticSearchFactoryUtil.getUserId(email),
        SearchOperators.EQUALS, not));
    final BoolQueryBuilder q = QueryBuilders.boolQuery().should(q1).should(q2);
    final List<String> groups = ElasticSearchFactoryUtil.getGroupsOfUser(userId);
    for (final String group : groups) {
      q.should(QueryBuilders.termsLookupQuery(ElasticFields.ID.field())
          .lookupIndex(ElasticService.DATA_ALIAS).lookupId(group)
          .lookupType(ElasticTypes.usergroups.name()).lookupPath(role.field()));
    }
    return q;
  }

  private static QueryBuilder contentQuery(QueryBuilder q) {
    return QueryBuilders.hasChildQuery(ElasticTypes.content.name(), q);
  }
}
