package ir.armansoft.telegram.gathering.repository;

import ir.armansoft.telegram.gathering.indices.Message;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends ElasticsearchRepository<Message, Long> {
}
