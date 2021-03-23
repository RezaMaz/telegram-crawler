package ir.armansoft.telegram.gathering.fetcher;

import ir.armansoft.telegram.gathering.indices.User;
import org.apache.lucene.search.join.ScoreMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Component;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Component
public class UserFetcher {
    private final FetcherUtil fetcherUtil;

    @Autowired
    public UserFetcher(FetcherUtil fetcherUtil) {
        this.fetcherUtil = fetcherUtil;
    }


    public Iterable<User> findUserForNewPhoto(String phone, int size) {
        return fetcherUtil.query(User.class, newPhotoSearchQuery(phone, size));
    }

    public Iterable<User> findUsersForFullInfo(String phone, int size) {
        return fetcherUtil.query(User.class, fullSearchQuery(phone, size));
    }

    //find user for get history photo
    public Iterable<User> findUserForHistoryPhoto(String phone, int size) {
        return fetcherUtil.query(User.class, historyPhotoSearchQuery(phone, size));
    }

    //findFullUsers - find users for get full information
    private SearchQuery fullSearchQuery(String phone, int size) {
        return getQueryBuilder(size)
                .withFilter(
                        boolQuery()
                                .must(
                                        nestedQuery("phoneInfo",
                                                boolQuery()
                                                        .must(termQuery("phoneInfo.number", phone))
                                                        .mustNot(existsQuery("phoneInfo.error")),
                                                ScoreMode.None))
                                .mustNot(existsQuery("fullDate"))
                                .mustNot(existsQuery("error"))
                )
                .build();
    }

    //find user for get photos
    private SearchQuery newPhotoSearchQuery(String phone, int size) {
        return getQueryBuilder(size)
                .withQuery(
                        fetcherUtil.random(
                                boolQuery()
                                        .must(
                                                nestedQuery("phoneInfo",
                                                        boolQuery()
                                                                .must(termQuery("phoneInfo.number", phone))
                                                                .must(existsQuery("phoneInfo.accessHash"))
                                                                .mustNot(existsQuery("phoneInfo.error"))
                                                        , ScoreMode.None)
                                        )
                                        .mustNot(existsQuery("minPhotoId"))
                                        .mustNot(existsQuery("visitLastPhoto"))
                        )
                )
                .build();
    }

    private SearchQuery historyPhotoSearchQuery(String phone, int size) {
        return getQueryBuilder(size)
                .withQuery(
                        fetcherUtil.random(
                                boolQuery()
                                        .must(
                                                nestedQuery("phoneInfo",
                                                        boolQuery()
                                                                .must(termQuery("phoneInfo.number", phone))
                                                                .must(existsQuery("phoneInfo.accessHash"))
                                                                .mustNot(existsQuery("phoneInfo.error"))
                                                        , ScoreMode.None)
                                        )
                                        .must(existsQuery("minPhotoId"))
                                        .mustNot(existsQuery("visitLastPhoto"))
                        )
                )
                .build();
    }

    //find users have username property
    public Iterable<User> findUsersWithUsername() {
        return fetcherUtil.query(User.class, userWithUsernameSearchQuery(10));
    }

    //find users have phone property
    public Iterable<User> findUsersWithPhone() {
        return fetcherUtil.query(User.class, userWithPhoneSearchQuery(10));
    }

    private SearchQuery userWithUsernameSearchQuery(int size) {
        return getQueryBuilder(size)
                .withFilter(existsQuery("username"))
                .build();
    }

    private SearchQuery userWithPhoneSearchQuery(int size) {
        return getQueryBuilder(size)
                .withFilter(existsQuery("phone"))
                .build();
    }

    private NativeSearchQueryBuilder getQueryBuilder(int size) {
        return new NativeSearchQueryBuilder()
                .withIndices(User.INDEX)
                .withPageable(PageRequest.of(0, size));
    }
}
