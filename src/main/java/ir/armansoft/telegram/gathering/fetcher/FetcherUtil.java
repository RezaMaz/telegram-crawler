package ir.armansoft.telegram.gathering.fetcher;

import ir.armansoft.telegram.gathering.indices.Channel;
import ir.armansoft.telegram.gathering.indices.Group;
import ir.armansoft.telegram.gathering.repository.ChannelRepository;
import ir.armansoft.telegram.gathering.repository.GroupRepository;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.functionScoreQuery;

@Component
public class FetcherUtil {

    private final ElasticsearchTemplate searchTemplate;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;

    @Autowired
    public FetcherUtil(ElasticsearchTemplate searchTemplate, ChannelRepository channelRepository, GroupRepository groupRepository) {
        this.searchTemplate = searchTemplate;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
    }

    <T> Iterable<T> query(Class<T> clazz, SearchQuery query) {
        return searchTemplate.queryForList(query, clazz);
    }

    //find channel by id
    public Channel findChannelById(int id) {
        if (!channelRepository.findById(id).isPresent())
            return null;
        return channelRepository.findById(id).get();
    }

    public Group findGroupById(int id) {
        if (!groupRepository.findById(id).isPresent())
            return null;
        return groupRepository.findById(id).get();
    }

    public QueryBuilder random(QueryBuilder queryBuilder) {
        return functionScoreQuery(
                queryBuilder,
                ScoreFunctionBuilders.randomFunction()
        );
    }
}
