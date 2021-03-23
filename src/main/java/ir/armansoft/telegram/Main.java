package ir.armansoft.telegram;

import com.github.badoualy.telegram.api.TelegramApp;
import com.google.common.collect.Maps;
import com.twitter.Extractor;
import ir.armansoft.telegram.gathering.Crawler;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.integration.IntegrationProperties;
import ir.armansoft.telegram.gathering.integration.TelegramClientFactoryBean;
import ir.armansoft.telegram.gathering.integration.impl.TelegramIntegrationImpl;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Slf4j
@EnableScheduling
@SpringBootApplication
public class Main implements CommandLineRunner, ApplicationContextAware {

    private final Map<String, Integer> newMessageDateMap = Maps.newHashMap();
    private final Map<String, Crawler> privateCrawlerHashMap = Maps.newHashMap();
    @Autowired
    private BulkRequestService bulkRequestService;
    @Autowired
    private IntegrationProperties integrationProperties;
    @Autowired
    private TelegramApp telegramApp;
    private ApplicationContext context;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Main.class);
        app.run(args);
    }

    @Override
    public void run(String... args) {
        for (String phone : integrationProperties.getPhones()) {
            newMessageDateMap.put(phone, 0);
            createClient(phone);
            createIntegration(phone);
            privateCrawlerHashMap.put(phone, createPrivateCrawler(phone));
        }
        crawler();
    }

    private Crawler createPrivateCrawler(String phone) {
        String beanId = "privateCrawler#" + phone;
        try {
            return context.getBean(beanId, Crawler.class);
        } catch (NoSuchBeanDefinitionException e) {
            BeanDefinition bdb = BeanDefinitionBuilder
                    .genericBeanDefinition(Crawler.class)
                    .setScope(SCOPE_SINGLETON)
                    .addConstructorArgReference("integration#" + phone)
                    .getBeanDefinition();
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ((ConfigurableApplicationContext) context).getBeanFactory();
            beanFactory.registerBeanDefinition(beanId, bdb);

            return context.getBean(beanId, Crawler.class);
        }
    }

    private void createClient(String phone) {
        String beanId = "client#" + phone;
        BeanDefinitionBuilder bdb = BeanDefinitionBuilder
                .genericBeanDefinition(TelegramClientFactoryBean.class)
                .setScope(SCOPE_PROTOTYPE)
                .addConstructorArgValue(phone)
                .addConstructorArgValue(telegramApp);

        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ((ConfigurableApplicationContext) context).getBeanFactory();

        beanFactory.registerBeanDefinition(beanId, bdb.getBeanDefinition());
    }

    private void createIntegration(String phone) {
        String beanId = "integration#" + phone;
        BeanDefinitionBuilder bdb = BeanDefinitionBuilder
                .genericBeanDefinition(TelegramIntegrationImpl.class)
                .setScope(SCOPE_PROTOTYPE)
                .addConstructorArgValue(phone)
                .addConstructorArgReference("client#" + phone);

        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) ((ConfigurableApplicationContext) context).getBeanFactory();

        beanFactory.registerBeanDefinition(beanId, bdb.getBeanDefinition());
    }

    private void crawler() {
        new Thread(() -> {
            while (true) {
                ThreadPoolExecutor executorPool = new ThreadPoolExecutor(2, 5, 10,
                        TimeUnit.SECONDS, new ArrayBlockingQueue<>(5));
                for (Map.Entry<String, Crawler> entry : privateCrawlerHashMap.entrySet()) {
                    executorPool.execute(() -> {
                        Crawler crawler = entry.getValue();

                        newMessageDateMap.put(entry.getKey(), crawler.lastMessage(newMessageDateMap.get(entry.getKey())));
                        crawler.updateChannelsForFullInfo(1);
                        crawler.indexChannelsForUpdateMessages(1);
                        crawler.indexChannelsForHistoryMessages(1);
                        crawler.searchUsername(1);
                        crawler.resolveHashCodes(1);
                        crawler.indexUsersForFullInfo(1);

                    });
                }

//                 Tell threads to finish off.
                executorPool.shutdown();
                try {
                    while (!executorPool.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.info("[{}/{}] Active: {}, Completed: {}, Task: {}, isShutdown: {}, isTerminated: {}",
                                executorPool.getPoolSize(),
                                executorPool.getCorePoolSize(),
                                executorPool.getActiveCount(),
                                executorPool.getCompletedTaskCount(),
                                executorPool.getTaskCount(),
                                executorPool.isShutdown(),
                                executorPool.isTerminated());
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (bulkRequestService.getCommitting()) {
                    log.info("bulk is working ...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        log.error("can't sleep", e);
                    }
                }
                log.info("bulk is completed!");
            }
        }, "crawler").start();
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext context) {
        this.context = context;
    }

    @Bean
    public TelegramApp getTelegramApp() {
        return new TelegramApp(integrationProperties.getApiId(), integrationProperties.getApiHash(),
                integrationProperties.getDeviceModel(), integrationProperties.getSystemVersion(),
                integrationProperties.getAppVersion(), integrationProperties.getLangCode());
    }

    @Bean
    public Extractor getExtractor() {
        return new Extractor();
    }
}

