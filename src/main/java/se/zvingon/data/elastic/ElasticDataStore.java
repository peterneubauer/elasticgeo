// header start
package se.zvingon.data.elastic;

import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.hppc.cursors.ObjectCursor;
import org.elasticsearch.common.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.NameImpl;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;


public class ElasticDataStore extends ContentDataStore {
// header end

    // constructor start
    String searchHost;
    String indexName;
    String geofield;
    String query;
    Integer hostPort;
    boolean localNode;
    boolean storeData;
    Node elasticSearchNode;
    private String clusterName;
    Client elasticSearchClient;
    ImmutableList<Name> cachedTypeNames;
    List<String> fields;
    boolean useFields;

    public ElasticDataStore(String searchHost,
                            Integer hostPort,
                            String indexName,
                            String clusterName,
                            String geofield,
                            boolean localNode,
                            boolean storeData,
                            String namespaceURI,
                            List<String> fields) {
        this.searchHost = searchHost;
        this.hostPort = hostPort;
        this.indexName = indexName;
        this.geofield = geofield;
        this.localNode = localNode;
        this.storeData = storeData;
        this.clusterName = clusterName;
        this.fields = fields;
        setNamespaceURI(namespaceURI);
        if (fields != null && fields.size() > 0) {
            useFields = true;
        }
        if (localNode) {
            initLocalNodeAndClient();
        } else {
            initTransportClient();
        }
        cacheTypeNames();
    }

    private void initLocalNodeAndClient() {
        NodeBuilder nodeBuilder = nodeBuilder().data(storeData).local(false).client(false).clusterName(clusterName);
        this.elasticSearchNode = nodeBuilder.build();
        elasticSearchNode.start();
        this.elasticSearchClient = elasticSearchNode.client();
        System.out.println("initLocalNodeAndClient - " + elasticSearchClient);
    }

    private void initTransportClient() {
        ImmutableSettings.Builder settings = ImmutableSettings.settingsBuilder().put("cluster.name", clusterName);
        this.elasticSearchClient = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress(searchHost, hostPort));
    }

    private void cacheTypeNames() {
        ClusterStateRequest clusterStateRequest = Requests.clusterStateRequest()
                .indices(indexName);

        ClusterState state = this.elasticSearchClient.admin().cluster().state(clusterStateRequest).actionGet().getState();
        MetaData indexMetaDatas = state.metaData();
        IndexMetaData index = indexMetaDatas.index(indexName);
        ImmutableOpenMap<String, MappingMetaData> mappings = index.mappings();
        Iterator<ObjectCursor<String>> elasticTypes = mappings.keys().iterator();
        Vector names = new Vector<Name>();
        while (elasticTypes.hasNext()) {
            names.add(new NameImpl(getNamespaceURI(), elasticTypes.next().value));
        }
        cachedTypeNames = ImmutableList.copyOf(names);
    }


    // createTypeNames start
    @Override
    protected List<Name> createTypeNames() throws IOException {
        return this.cachedTypeNames;
    }
    // createTypeNames end


    @Override
    protected ContentFeatureSource createFeatureSource(ContentEntry entry) throws IOException {
        return new ElasticFeatureSource(entry, Query.ALL);
    }


    @Override
    public FeatureReader<SimpleFeatureType, SimpleFeature> getFeatureReader(Query query, Transaction tx) throws IOException {
        // todo use query?
        return super.getFeatureReader(query, tx);
    }

    @Override
    public void dispose() {
        if (elasticSearchClient != null) {
            this.elasticSearchClient.close();
        }
        if (elasticSearchNode != null) {
            this.elasticSearchNode.stop();
            this.elasticSearchNode.close();
        }
        super.dispose();
    }

    public List<String> getFields() {
        return fields;
    }
}
