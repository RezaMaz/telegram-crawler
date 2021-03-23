package ir.armansoft.telegram.gathering.fetcher;

import ir.armansoft.telegram.gathering.indices.UserName;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Component
public class UsernameFetcher {
    private final FetcherUtil fetcherUtil;

    @Autowired
    public UsernameFetcher(FetcherUtil fetcherUtil) {
        this.fetcherUtil = fetcherUtil;
    }


    //find username not verified as channel or user yet
    public Iterable<UserName> findNotVerifiedUserNames(int size) {
        return fetcherUtil.query(UserName.class, notVerifiedQuery(size));
    }

    private SearchQuery notVerifiedQuery(int size) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
                .mustNot(nestedQuery("phoneInfo",
                        existsQuery("phoneInfo"),
                        ScoreMode.None))
                .mustNot(existsQuery("error"));
        return new NativeSearchQueryBuilder()
                .withIndices(UserName.INDEX)
                .withQuery(
                        fetcherUtil.random(boolQueryBuilder)
                )
                .withPageable(PageRequest.of(0, size))
                .build();
    }
}
