package ir.armansoft.telegram.gathering.repository;

import ir.armansoft.telegram.gathering.indices.User;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends ElasticsearchRepository<User, Long> {
}
