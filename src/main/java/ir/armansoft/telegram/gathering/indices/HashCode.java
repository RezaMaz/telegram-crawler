package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Map;

@Setter
@Getter
@Document(indexName = HashCode.INDEX, type = "_doc")
public class HashCode {
    public static final String INDEX = "hashcode";

    @Id
    private String id;
    private Map<String, Object> phoneInfo;
}
