package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;
import java.util.Map;

@Setter
@Getter
@Document(indexName = Channel.INDEX, type = "_doc")
public class Channel implements Resource<Integer> {
    public static final String INDEX = "channel";

    @Id
    private Integer id;

    private String title;

    private String username;

    private Date date;

    private Map<String, Object> phoneInfo;

    private String language;

    private Long participantsCount;

    public String getIndex() {
        return INDEX;
    }
}
