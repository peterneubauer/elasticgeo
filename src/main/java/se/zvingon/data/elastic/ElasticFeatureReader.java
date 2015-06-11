package se.zvingon.data.elastic;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.store.ContentState;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.filter.visitor.ExtractBoundsFilterVisitor;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;


public class ElasticFeatureReader implements FeatureReader<SimpleFeatureType, SimpleFeature> {

    protected ContentState state;
    private SimpleFeature next;
    protected SimpleFeatureBuilder builder;
    private int row;
    private GeometryFactory geometryFactory;
    ElasticDataStore dataStore;
    long count = 0;
    SearchResponse response;
    Iterator<SearchHit> searchHitIterator;
    private final Filter filter;

    Logger logger = Logger.getLogger(ElasticFeatureReader.class.getSimpleName());

    public ElasticFeatureReader(ContentState contentState, Query query) throws IOException {


        this.state = contentState;
        SimpleFeatureType type = state.getFeatureType();
        builder = new SimpleFeatureBuilder(type);
        geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
        row = 1;
        dataStore = (ElasticDataStore) contentState.getEntry().getDataStore();
        ELSAdapter elsAdapter = new ELSAdapter();
        filter = query.getFilter();
        FilterVisitor visitor = ExtractBoundsFilterVisitor.BOUNDS_VISITOR;
        Envelope result = (Envelope) filter.accept(visitor, DefaultGeographicCRS.WGS84);
        System.out.println("Read based on envelope: " + result.toString());

        try {

            //make a default query
            QueryBuilder searchforFilter = QueryBuilders.boolQuery().
                    must(elsAdapter.getSearchforFilter(filter))
                    .must(QueryBuilders.geoShapeQuery("l_shape", ShapeBuilder.newEnvelope()
                                    .topLeft(result.getMinX() == Double.NEGATIVE_INFINITY ? -180 : result.getMinX(), result.getMaxY() == Double.POSITIVE_INFINITY ? 90 : result.getMaxY())
                                    .bottomRight(result.getMaxX() == Double.POSITIVE_INFINITY ? 180 : result.getMaxX(), result.getMinY() == Double.NEGATIVE_INFINITY ? -90 : result.getMinY())
                    ));
            SearchResponse countRequest = dataStore.elasticSearchClient.prepareSearch(dataStore.indexName)
                    .setTypes(dataStore.getTypeNames())
                    .setSearchType(SearchType.COUNT)
                    .setQuery(searchforFilter)
                    .execute().get();

            count = countRequest.getHits().getTotalHits();

            logger.info("Found " + count + " features matching bbox");
            logger.info("Trying to retrieve: ");

            List<AttributeType> attributes = type.getTypes();

            response = dataStore.elasticSearchClient.prepareSearch(dataStore.indexName)
                    .setTypes(dataStore.getTypeNames())
                    .setQuery(searchforFilter)
                    .setFrom(0)
                    .setSize(Math.min(new Long(count).intValue(), query.getMaxFeatures()))
                    .addFields(new String[]{"_source"})
                    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                    .execute().get();

            System.out.println(response.getHits().getTotalHits());
            searchHitIterator = response.getHits().iterator();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public SimpleFeatureType getFeatureType() {
        return state.getFeatureType();
    }

    public SimpleFeature next() throws IOException, IllegalArgumentException,
            NoSuchElementException {
        SimpleFeature feature;
        if (next != null) {
            feature = next;
            next = null;
        } else {
            feature = readFeature();
        }
        return feature;
    }

    SimpleFeature readFeature() throws IOException {
        if (row > count) {
            return null;
        }

        if (!searchHitIterator.hasNext()) {
            return null;
        }


        SearchHit hit = searchHitIterator.next();
        SimpleFeatureType type = getFeatureType();


        for (AttributeType attributeType : type.getTypes()) {
            String propertyKey = attributeType.getName().getLocalPart();
            Map<String, Object> source = hit.getSource();
            if (source.containsKey(propertyKey)) {
                Object field = source.get(propertyKey);

                if (Point.class.equals(attributeType.getBinding())) {
                    Coordinate coordinate = new Coordinate();
                    Map<String, Object> location = (Map<String, Object>) field;
                    // this fix this

                    if (location.containsKey("lat") && location.containsKey("lon")) {
                        coordinate.y = (Double) location.get("lat");
                        coordinate.x = (Double) location.get("lon");
                        builder.set(propertyKey, geometryFactory.createPoint(coordinate));
                    }
                } else {
                    Object value = field;
                    if (value != null) {
                        builder.set(propertyKey, value);
                    }

                }
            }
        }

        return this.buildFeature();
    }

    protected SimpleFeature buildFeature() {
        row += 1;
        return builder.buildFeature(state.getEntry().getTypeName() + "." + row);
    }

    public boolean hasNext() throws IOException {
        if (next != null) {
            return true;
        } else {
            next = readFeature();
            return next != null;
        }
    }

    public void close() throws IOException {
        builder = null;
        geometryFactory = null;
        next = null;
        searchHitIterator = null;
        response = null;
    }

}
