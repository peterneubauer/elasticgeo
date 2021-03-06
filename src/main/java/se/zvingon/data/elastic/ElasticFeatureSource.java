package se.zvingon.data.elastic;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.visitor.ExtractBoundsFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.elasticsearch.index.query.FilterBuilders.geoBoundingBoxFilter;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@SuppressWarnings("unchecked")
public class ElasticFeatureSource extends ContentFeatureStore {

    public ElasticFeatureSource(ContentEntry entry, Query query) {
        super(entry, query);
    }
    Logger logger = Logger.getLogger(ElasticFeatureReader.class.getSimpleName());

    /**
     * Access parent datastore
     */
    public ElasticDataStore getDataStore() {
        return (ElasticDataStore) super.getDataStore();
    }

    @Override
    protected boolean canFilter() {
        return true;
    }

    /**
     * Implementation that generates the total bounds
     */
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        ReferencedEnvelope bounds = new ReferencedEnvelope(getSchema().getCoordinateReferenceSystem());

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader = getReaderInternal(query);
        try {
            while (featureReader.hasNext()) {
                SimpleFeature feature = featureReader.next();
                bounds.include(feature.getBounds());
            }
        } finally {
            featureReader.close();
        }
        return bounds;
    }

    protected int getCountInternal(Query query) throws IOException {
        logger.info("getCountInternal");
        ElasticDataStore dataStore = getDataStore();
        Filter filter = query.getFilter();
        FilterVisitor visitor = ExtractBoundsFilterVisitor.BOUNDS_VISITOR;
        Envelope result = (Envelope) filter.accept(visitor, DefaultGeographicCRS.WGS84);
        //make a default query
        QueryBuilder searchforFilter = QueryBuilders.boolQuery().
                must(new ELSAdapter().getSearchforFilter(filter))
                .must(QueryBuilders.geoShapeQuery("l_shape", ShapeBuilder.newEnvelope()
                                .topLeft(result.getMinX() == Double.NEGATIVE_INFINITY ? -180 : result.getMinX(), result.getMaxY() == Double.POSITIVE_INFINITY ? 90 : result.getMaxY())
                                .bottomRight(result.getMaxX() == Double.POSITIVE_INFINITY ? 180 : result.getMaxX(), result.getMinY() == Double.NEGATIVE_INFINITY ? -90 : result.getMinY())
                ));

        SearchResponse countRequest = null;
        try {
            countRequest = dataStore.elasticSearchClient.prepareSearch(dataStore.indexName)
                    .setTypes(dataStore.getTypeNames())
                    .setSearchType(SearchType.COUNT)
                    .setQuery(searchforFilter)
                    .execute().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return new Long(countRequest.getHits().getTotalHits()).intValue();
    }

    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query query)
            throws IOException {
        logger.info("getReaderInternal");
        return new ElasticFeatureReader(getState(), query);
    }

    protected SimpleFeatureType buildFeatureType() throws IOException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(entry.getName());

        ElasticDataStore dataStore = getDataStore();

        ClusterStateRequest clusterStateRequest = Requests.clusterStateRequest()
                .indices(dataStore.indexName);

        ClusterState state = dataStore.elasticSearchClient.admin().cluster().state(clusterStateRequest).actionGet().getState();
        MappingMetaData metadata = state.metaData().index(dataStore.indexName).mapping(entry.getName().getLocalPart());

        byte[] mappingSource = metadata.source().uncompressed();
        XContentParser parser = XContentFactory.xContent(mappingSource).createParser(mappingSource);
        Map<String, Object> mapping = parser.map();
        if (mapping.size() == 1 && mapping.containsKey(entry.getName().getLocalPart())) {
            // the type name is the root value, reduce it
            mapping = (Map<String, Object>) mapping.get(entry.getName().getLocalPart());
        }

        if (mapping.containsKey("properties")) {
            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) mapping.get("properties");

            for (String propertyKey : properties.keySet()) {
                if (dataStore.useFields && !dataStore.getFields().contains(propertyKey))
                    continue;

                Map<String, Object> property = properties.get(propertyKey);

                if (property.containsKey("type")) {
                    String propertyType = (String) property.get("type");

                    if ("geo_point".equalsIgnoreCase(propertyType)) {
                        builder.setCRS(DefaultGeographicCRS.WGS84);
                        builder.add(propertyKey, Point.class);
                        builder.setSRS("EPSG:4326");
                    }
                    if ("string".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, String.class);
                    }
                    if ("integer".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, Integer.class);
                    }
                    if ("long".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, Long.class);
                    }
                    if ("float".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, Float.class);
                    }
                    if ("double".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, Double.class);
                    }
                    if ("boolean".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, Boolean.class);
                    }
                    if ("nested".equalsIgnoreCase(propertyType)) {
                        builder.add(propertyKey, Map.class);
                    }
                }
            }
        }
        final SimpleFeatureType SCHEMA = builder.buildFeatureType();
        System.out.println("buildFeatureType:" + SCHEMA.toString());
        return SCHEMA;
    }

    @Override
    protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(Query query, int flags) throws IOException {
        return null;
    }

}
