// header start
package se.zvingon.data.elastic;

import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.MappingMetaData;
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

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;


public class ElasticDataStore extends ContentDataStore {
// header end

    // constructor start
    String searchHost;
    String indexName;
    String query;
    Integer hostPort;
    boolean localNode;
    boolean storeData;
    Node elasticSearchNode;
    private String clusterName;

    public ElasticDataStore(String searchHost, Integer hostPort, String indexName, String clusterName, String query, boolean localNode, boolean storeData) {
        this.searchHost = searchHost;
        this.hostPort = hostPort;
        this.indexName = indexName;
        this.query = query;
        this.localNode = localNode;
        this.storeData = storeData;
        this.clusterName = clusterName;
        if (localNode)
            initLocalNode();
    }

    private void initLocalNode() {
        NodeBuilder nodeBuilder = nodeBuilder().data(storeData).local(false).client(false).clusterName(clusterName);
        this.elasticSearchNode = nodeBuilder.build();
        elasticSearchNode.start();
    }

    // createTypeNames start
    @Override
    protected List<Name> createTypeNames() throws IOException {
        System.out.println("Create Type Names");
        TransportClient client = new TransportClient().addTransportAddress(new InetSocketTransportAddress(searchHost, hostPort));
        ClusterStateRequest clusterStateRequest = Requests.clusterStateRequest()
                .filterRoutingTable(true)
                .filterNodes(true)
                .filteredIndices(indexName);

        ClusterState state = client.admin().cluster().state(clusterStateRequest).actionGet().getState();
        Map<String, MappingMetaData> mappings = state.metaData().index(indexName).mappings();
        Iterator<String> elasticTypes = mappings.keySet().iterator();
        List<Name> types = new Vector<Name>();
        while (elasticTypes.hasNext()) {
            types.add(new NameImpl(elasticTypes.next()));
        }
        return types;
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
        this.elasticSearchNode.stop();
        this.elasticSearchNode.close();
        super.dispose();
    }
}
