package se.zvingon.data.elastic;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.geotools.filter.IsEqualsToImpl;
import org.opengis.filter.Filter;

import java.util.logging.Logger;

/**
 * Created by peterneubauer on 6/11/15.
 */
public class ELSAdapter {
    Logger logger = Logger.getLogger(ELSAdapter.class.getSimpleName());

    public QueryBuilder getSearchforFilter(Filter filter) {
        if(filter==null) {
            return QueryBuilders.matchAllQuery();
        }
        if(filter instanceof IsEqualsToImpl) {
            IsEqualsToImpl equals = (IsEqualsToImpl) filter;
            QueryBuilder buidler = QueryBuilders.matchQuery(equals.getExpression1().toString(), equals.getExpression2().toString());
            return buidler;
        }
        logger.info(String.format("no adapter for query %s", filter));
        return QueryBuilders.matchAllQuery();
    }
}
