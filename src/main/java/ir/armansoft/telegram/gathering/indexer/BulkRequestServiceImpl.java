package ir.armansoft.telegram.gathering.indexer;

import com.google.common.collect.Queues;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class BulkRequestServiceImpl implements BulkRequestService {

    private final Client client;

    private final AtomicBoolean committing = new AtomicBoolean(false);

    private Queue<? super DocWriteRequest> requestQueue;

    @Autowired
    public BulkRequestServiceImpl(Client client) {
        this.client = client;
    }

    @PostConstruct
    public void init() {
        requestQueue = Queues.newConcurrentLinkedQueue();
    }

    @Override
    public void add(IndexRequest request) {
        requestQueue.add(request);
    }

    @Override
    public void add(DeleteRequest request) {
        requestQueue.add(request);
    }

    @Override
    public void add(UpdateRequest request) {
        requestQueue.add(request);
    }

    @Override
    public void add(IndexRequestBuilder builder) {
        requestQueue.add(builder.request());
    }

    @Override
    public void add(DeleteRequestBuilder builder) {
        requestQueue.add(builder.request());
    }

    @Override
    public void add(UpdateRequestBuilder builder) {
        requestQueue.add(builder.request());
    }

    @PreDestroy
    @Scheduled(fixedDelayString = "${other.index.commit.delay}")
    public void action() {
        if (!requestQueue.isEmpty()) {
            BulkRequestBuilder builder = client.prepareBulk();
            while (!requestQueue.isEmpty()) {
                Object request = requestQueue.poll();
                if (request instanceof IndexRequest) {
                    builder.add((IndexRequest) request);
                } else if (request instanceof UpdateRequest) {
                    builder.add((UpdateRequest) request);
                } else if (request instanceof DeleteRequest) {
                    builder.add((DeleteRequest) request);
                } else {
                    log.info("unsupported request: {} ", request.getClass());
                }
            }

            if (builder.numberOfActions() > 0) {
                committing.set(true);
                BulkResponse response = builder.get();
                log.info(response.getItems().length + " items is index at " + response.getTook().millis() + " ms.");
                if (response.hasFailures())
                    log.error(response.buildFailureMessage());
                committing.set(false);
            }
        }
    }

    @Override
    public boolean getCommitting() {
        return committing.get();
    }
}
