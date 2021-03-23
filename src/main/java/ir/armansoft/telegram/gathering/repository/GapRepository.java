package ir.armansoft.telegram.gathering.repository;

import ir.armansoft.telegram.gathering.indices.Gap;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GapRepository extends ElasticsearchRepository<Gap, String> {
    List<Gap> findGapByChatIdOrderByTelegramIdDesc(int chatId);
}
