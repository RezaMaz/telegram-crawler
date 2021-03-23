package ir.armansoft.telegram.gathering.fetcher;

import ir.armansoft.telegram.gathering.indices.HashCode;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Component;

@Component
public class HashCodeFetcher {
    private final FetcherUtil fetcherUtil;

    @Autowired
    public HashCodeFetcher(FetcherUtil fetcherUtil) {
        this.fetcherUtil = fetcherUtil;
    }


    //find hashcode not verified as channel or group
    public Iterable<HashCode> findNotVerifiedHashCodes(int size) {
        return fetcherUtil.query(HashCode.class, notVerifiedQuery(size));
    }

    private SearchQuery notVerifiedQuery(int size) {
        return new NativeSearchQueryBuilder()
                .withIndices(HashCode.INDEX)
                .withQuery(
                        fetcherUtil.random(

                                QueryBuilders.boolQuery()
                                        .mustNot(
                                                QueryBuilders.nestedQuery("phoneInfo",
                                                        QueryBuilders.matchAllQuery(),
                                                        ScoreMode.None)
                                        ).mustNot(QueryBuilders.existsQuery("error"))

                        )
                )
                .withPageable(PageRequest.of(0, size))
                .build();
    }
}
