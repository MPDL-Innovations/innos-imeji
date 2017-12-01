package de.mpg.imeji.logic.search.elasticsearch.factory.util;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filters.Filters;
import org.elasticsearch.search.aggregations.bucket.filters.Filters.Bucket;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.stats.InternalStats;

import de.mpg.imeji.logic.search.elasticsearch.factory.ElasticAggregationFactory;
import de.mpg.imeji.logic.search.facet.FacetService;
import de.mpg.imeji.logic.search.facet.model.Facet;
import de.mpg.imeji.logic.search.facet.model.FacetResult;
import de.mpg.imeji.logic.search.facet.model.FacetResultValue;

/**
 * Parse elasticSearchAggregation created by {@link ElasticAggregationFactory}
 * 
 * @author saquet
 *
 */
public class AggregationsParser {

  private AggregationsParser() {

  }

  /**
   * Parse a the Aggregation part of a SearchResponse and return a list of {@link FacetResult}.
   * Note: the aggregation must have been created by {@link ElasticAggregationFactory}
   * 
   * @param resp
   * @return
   */
  public static List<FacetResult> parse(SearchResponse resp) {
    List<FacetResult> facetResults = new ArrayList<>();
    if (resp != null && resp.getAggregations() != null) {
      Nested metadata = resp.getAggregations().get("metadata");
      if (metadata != null) {
        for (Aggregation mdAgg : metadata.getAggregations()) {
          FacetResult facetResult = new FacetResult(getFacetName(mdAgg.getName()), mdAgg.getName());
          if (mdAgg instanceof Filter) {
            Aggregation terms = ((Filter) mdAgg).getAggregations().asList().get(0);
            if (terms instanceof StringTerms) {
              for (Terms.Bucket bucket : ((StringTerms) terms).getBuckets()) {
                facetResult.getValues()
                    .add(new FacetResultValue(bucket.getKeyAsString(), bucket.getDocCount()));
              }
            } else if (terms instanceof InternalStats) {
              FacetResultValue result =
                  new FacetResultValue(terms.getName(), ((InternalStats) terms).getCount());
              result.setMax(((InternalStats) terms).getMaxAsString());
              result.setMin(((InternalStats) terms).getMinAsString());
              facetResult.getValues().add(result);
            } else {
              System.out.println("NOT PARSED  METADATA AGGREGATION: " + terms);
            }
          }
          facetResults.add(facetResult);
        }
        Filters system = resp.getAggregations().get("system");
        if (system != null) {
          Bucket systemBucket = system.getBucketByKey("all");
          for (Aggregation agg : systemBucket.getAggregations()) {
            FacetResult facetResult = new FacetResult(getFacetName(agg.getName()), agg.getName());
            if (agg instanceof Filters) {
              for (Filters.Bucket bucket : ((Filters) agg).getBuckets()) {
                facetResult.getValues()
                    .add(new FacetResultValue(bucket.getKeyAsString(), bucket.getDocCount()));
              }
            } else if (agg instanceof StringTerms) {
              for (Terms.Bucket bucket : ((StringTerms) agg).getBuckets()) {
                facetResult.getValues()
                    .add(new FacetResultValue(bucket.getKeyAsString(), bucket.getDocCount()));
              }

            }
            facetResults.add(facetResult);
          }
        }
        facetResults.add(parseInternalFacet(resp, Facet.ITEMS));
        facetResults.add(parseInternalFacet(resp, Facet.SUBCOLLECTIONS));
        if (resp.getAggregations().get(Facet.COLLECTION_ITEMS) != null) {
          facetResults.add(parseInternalFacet(resp, Facet.COLLECTION_ITEMS));
        }
      }
    }
    return facetResults;
  }

  /**
   * Parse the internal facets
   * 
   * @param resp
   * @param facetName
   * @return
   */
  private static FacetResult parseInternalFacet(SearchResponse resp, String facetName) {
    FacetResult f = new FacetResult(facetName, facetName);
    f.getValues().add(new FacetResultValue("count",
        ((Filters) resp.getAggregations().get(facetName)).getBucketByKey(facetName).getDocCount()));
    return f;
  }



  private static String getFacetName(String index) {
    final FacetService service = new FacetService();
    Facet facet = service.retrieveByIndexFromCache(index);
    return facet != null ? facet.getName() : index;
  }

}
