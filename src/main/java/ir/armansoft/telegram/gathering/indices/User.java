package ir.armansoft.telegram.gathering.indices;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Map;

@Setter
@Getter
@Document(indexName = User.INDEX, type = "_doc")
public class User {
    public static final String INDEX = "user";

    @Id
    private Integer id;
    private String phone;
    private String firstName;
    private String lastName;
    private String username;
    private Map<String, Object> phoneInfo;
    private Long minPhotoId;
    private Boolean visitLastPhoto;
}
