package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Setter
@Getter
@Document(indexName = Gap.INDEX, type = "_doc")
public class Gap {
    public static final String INDEX = "gap";

    @Id
    private String id;
    private int chatId;
    private int telegramId;

    public String getIndex() {
        return INDEX;
    }
}
