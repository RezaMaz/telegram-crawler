package ir.armansoft.telegram.gathering.fetcher;

import ir.armansoft.telegram.gathering.indices.Channel;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Component
public class ChannelFetcher {

    private final FetcherUtil fetcherUtil;

    @Autowired
    public ChannelFetcher(FetcherUtil fetcherUtil) {
        this.fetcherUtil = fetcherUtil;
    }

    //find channels(megagroup) for get participant
    public Iterable<Channel> findParticipant(String phone, int size) {
        return fetcherUtil.query(Channel.class, participantQuery(phone, size));
    }

    //find channels for get update(new) messages
    public Iterable<Channel> findUpdateMessages(String phone, int size) {
        return fetcherUtil.query(Channel.class, updateMessagesQuery(phone, size));
    }

    //find channels for get history messages base on gaps
    public Iterable<Channel> findHistoryMessages(String phone, int size) {
        return fetcherUtil.query(Channel.class, historyMessagesQuery(phone, size));
    }

    //find channels for update full information(per day)
    public Iterable<Channel> findUpdateChannelsForFullInfo(String phone, int size) {
        return fetcherUtil.query(Channel.class, updateFullInfoQuery(phone, size));
    }

    //find channel by username
    public Channel findChannelByUsername(String username, String phone, int size) {
        return fetcherUtil.query(Channel.class, ChannelByUsernameQuery(username, phone, size)).iterator().next();
    }

    private SearchQuery ChannelByUsernameQuery(String username, String phone, int size) {
        BoolQueryBuilder boolQueryBuilder = boolQuery().must(
                nestedQuery("phoneInfo",
                        boolQuery()
                                .must(termQuery("phoneInfo.number", phone))
                                .mustNot(existsQuery("phoneInfo.error")),
                        ScoreMode.None)
        )
                .must(matchQuery("username", username));
        return getQueryBuilder(size)
                .withQuery(
                        fetcherUtil.random(boolQueryBuilder)
                )
                .build();
    }

    private SearchQuery updateMessagesQuery(String phone, int size) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
                .must(
                        nestedQuery("phoneInfo",
                                boolQuery()
                                        .must(termQuery("phoneInfo.number", phone))
                                        .mustNot(existsQuery("phoneInfo.error"))
                                        .must(
                                                boolQuery()
                                                        .should(boolQuery().mustNot(existsQuery("phoneInfo.crawlDate")))
                                                        .should(rangeQuery("phoneInfo.nextCrawlDate").lt("now"))
                                        ),
                                ScoreMode.None))
                .must(matchQuery("megagroup", false))
                .must(existsQuery("fullDate"));//we need participantsCount
        return getQueryBuilder(size)
                .withQuery(boolQueryBuilder)
                .build();
    }

    private SearchQuery updateFullInfoQuery(String phone, int size) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
                .must(
                        nestedQuery("phoneInfo",
                                boolQuery()
                                        .must(termQuery("phoneInfo.number", phone))
                                        .mustNot(existsQuery("phoneInfo.error")),
                                ScoreMode.None))
                .must(matchQuery("megagroup", false))
                .must(
                        boolQuery()
                                .should(boolQuery().mustNot(existsQuery("fullDate")))
                                .should(rangeQuery("nextFullDate").lt("now"))
                );

        return getQueryBuilder(size)
                .withQuery(
                        fetcherUtil.random(boolQueryBuilder)
                ).build();
    }

    private SearchQuery historyMessagesQuery(String phone, int size) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
                .must(
                        nestedQuery("phoneInfo",
                                boolQuery()
                                        .must(termQuery("phoneInfo.number", phone))
                                        .mustNot(existsQuery("phoneInfo.error"))
                                        .must(rangeQuery("phoneInfo.gapNumber").gt(0))
                                , ScoreMode.None)
                )
                .must(matchQuery("megagroup", false));
        return getQueryBuilder(size)
                .withQuery(
                        fetcherUtil.random(boolQueryBuilder)
                ).withSort(SortBuilders.fieldSort("phoneInfo.crawlDate").order(SortOrder.ASC))
                .build();
    }

    private SearchQuery participantQuery(String phone, int size) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
                .must(nestedQuery("phoneInfo",
                        boolQuery()
                                .must(termQuery("phoneInfo.number", phone))
                                .mustNot(existsQuery("phoneInfo.error")),
                        ScoreMode.None))
                .mustNot(existsQuery("participantDate"))
                .must(termQuery("megagroup", true));
        return getQueryBuilder(size)
                .withQuery(
                        fetcherUtil.random(boolQueryBuilder)
                )
                .build();
    }

    private NativeSearchQueryBuilder getQueryBuilder(int size) {
        return new NativeSearchQueryBuilder()
                .withIndices(Channel.INDEX)
                .withPageable(PageRequest.of(0, size));
    }
}
