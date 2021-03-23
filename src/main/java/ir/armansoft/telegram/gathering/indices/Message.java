package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;
import java.util.Map;

@Setter
@Getter
@Document(indexName = Message.INDEX, type = "_doc")
public class Message {
    public static final String INDEX = "msg";

    @Id
    private Long id;
    private Integer telegramId;
    private Map<String, Object> fwdFrom;
    private Integer viaBotId;
    private Date date;
    private String message;
    private Integer views;
    private Map media;
    private Map<String, Object> toId;
}
