package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Map;

@Setter
@Getter
@Document(indexName = UserName.INDEX, type = "_doc")
public class UserName {
    public static final String INDEX = "username";

    @Id
    private String id;
    private Map<String, Object> phoneInfo;
}
