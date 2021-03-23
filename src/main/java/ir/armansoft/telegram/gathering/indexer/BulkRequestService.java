package ir.armansoft.telegram.gathering.indexer;

import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;

public interface BulkRequestService {
    boolean getCommitting();

    void add(IndexRequest request);

    void add(DeleteRequest request);

    void add(UpdateRequest request);

    void add(IndexRequestBuilder request);

    void add(DeleteRequestBuilder request);

    void add(UpdateRequestBuilder request);
}
