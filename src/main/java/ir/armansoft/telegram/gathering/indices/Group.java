package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;
import java.util.Map;

@Setter
@Getter
@Document(indexName = Group.INDEX, type = "_doc")
public class Group implements Resource<Integer> {
    public static final String INDEX = "group";

    @Id
    private Integer id;

    private String title;

    private Integer participantsCount;

    private Date date;

    private Map<String, Object> phoneInfo;

    public String getIndex() {
        return INDEX;
    }
}
