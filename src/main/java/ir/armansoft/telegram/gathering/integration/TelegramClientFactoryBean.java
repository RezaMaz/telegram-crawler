package ir.armansoft.telegram.gathering.integration;

import com.github.badoualy.telegram.api.Kotlogram;
import com.github.badoualy.telegram.api.TelegramApp;
import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.api.TLUser;
import com.github.badoualy.telegram.tl.api.auth.TLAuthorization;
import com.github.badoualy.telegram.tl.api.auth.TLSentCode;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import java.io.IOException;
import java.util.Scanner;

public class TelegramClientFactoryBean extends AbstractFactoryBean<TelegramClient> {

    private final String phone;

    private final TelegramApp telegramApp;

    public TelegramClientFactoryBean(String phone, TelegramApp telegramApp) {
        this.phone = phone;
        this.telegramApp = telegramApp;
    }

    //This method is required for autowiring to work correctly
    @Override
    public Class<TelegramClient> getObjectType() {
        return TelegramClient.class;
    }

    //This method will be called by container to create new instances
    @NotNull
    @Override
    protected TelegramClient createInstance() throws Exception {
        ApiStorage storage = new ApiStorage(phone);
        // This is a synchronous client, that will block until the response arrive (or until timeout)
        TelegramClient client = Kotlogram.getDefaultClient(telegramApp, storage);
        try {
            client.updatesGetState();
            logger.info("client#" + phone + " have already signed.");
        } catch (RpcErrorException e) {
            if (e.getCode() == 401) {
                // You can start making requests
                try {
                    // Send code to account
                    TLSentCode sentCod = client.authSendCode(false, phone, true);
                    logger.info("Authentication code : " + phone);
                    String code = new Scanner(System.in).nextLine();

                    // Auth with the received code
                    TLAuthorization authorization = client.authSignIn(phone, sentCod.getPhoneCodeHash(), code);
                    TLUser self = authorization.getUser().getAsUser();
                    logger.info("You are now signed in as " + self.getFirstName() + " " + self.getLastName() + " @" + self.getUsername());
                } catch (RpcErrorException | IOException e1) {
                    e1.printStackTrace();
                } finally {
                    client.close(); // Important, do not forget this, or your process won't finish
                }
            }
        }
        return client;
    }
}
