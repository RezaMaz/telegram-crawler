package ir.armansoft.telegram.gathering.repository;

import ir.armansoft.telegram.gathering.indices.Channel;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelRepository extends ElasticsearchRepository<Channel, Integer> {
}
