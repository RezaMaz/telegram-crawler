package ir.armansoft.telegram.gathering.repository;

import ir.armansoft.telegram.gathering.indices.Group;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends ElasticsearchRepository<Group, Integer> {
}
