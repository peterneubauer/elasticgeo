package se.zvingon.data.elastic;

import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.geotools.filter.FilterFactoryImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.junit.Assert;
import org.junit.Test;
import org.opengis.filter.Filter;

import static org.junit.Assert.assertTrue;

/**
 * Created by peterneubauer on 6/11/15.
 */
public class TestECQLConverter {


    @Test
    public void testStringConversion() throws CQLException {
        FilterFactoryImpl factory = new FilterFactoryImpl();
        ELSAdapter els = new ELSAdapter();
        assertTrue(els.getSearchforFilter(null) instanceof MatchAllQueryBuilder);
        assertTrue(els.getSearchforFilter(ECQL.toFilter("name='bob'")) instanceof MatchQueryBuilder);
        assertTrue(els.getSearchforFilter(ECQL.toFilter("name LIKE 'bob'")) instanceof MatchAllQueryBuilder);
    }
}
