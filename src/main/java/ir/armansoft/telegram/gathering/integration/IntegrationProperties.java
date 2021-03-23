package ir.armansoft.telegram.gathering.integration;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "telegram.integration")
public class IntegrationProperties {
    private int apiId;
    private String apiHash;
    private String deviceModel;
    private String systemVersion;
    private String appVersion;
    private String langCode;
    private String ip;
    private List<String> phones = Lists.newArrayList();
}
