package ir.armansoft.telegram.gathering.integration;

import com.github.badoualy.telegram.api.TelegramApiStorage;
import com.github.badoualy.telegram.mtproto.auth.AuthKey;
import com.github.badoualy.telegram.mtproto.model.DataCenter;
import com.github.badoualy.telegram.mtproto.model.MTSession;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Implement ApiStorage to save your connection, to avoid having to use sendCode every time you launch your app
 */
public class ApiStorage implements TelegramApiStorage {
    private static final Logger logger = LoggerFactory.getLogger(ApiStorage.class);

    //set $MODULE_WORKING_DIR$
    private static final String PREFIX = "./storage" + File.separator;

    private final File authKeyFile;

    private final File nearestDcFile;

    //Create File variable for auth.key and dc.save
    ApiStorage(String phone) {
        String path = PREFIX + phone + File.separator;
        this.authKeyFile = new File(path + "auth.key");
        this.nearestDcFile = new File(path + "dc.save");
    }

    @Override
    public void saveAuthKey(@NotNull AuthKey authKey) {
        try {
            FileUtils.writeByteArrayToFile(authKeyFile, authKey.getKey());
        } catch (IOException e) {
            logger.error("can not save auth key.", e);
        }
    }

    @Nullable
    @Override
    public AuthKey loadAuthKey() {
        try {
            return new AuthKey(FileUtils.readFileToByteArray(authKeyFile));
        } catch (IOException e) {
            if (!(e instanceof FileNotFoundException))
                logger.error("can not load auth key.", e);
        }

        return null;
    }

    @Override
    public void saveDc(@NotNull DataCenter dataCenter) {
        try {
            FileUtils.write(nearestDcFile, dataCenter.toString(), Charset.defaultCharset());
        } catch (IOException e) {
            logger.error("can not save dc.", e);
        }
    }

    @Nullable
    @Override
    public DataCenter loadDc() {
        try {
            String[] infos = FileUtils.readFileToString(nearestDcFile, Charset.defaultCharset()).split(":");
            return new DataCenter(infos[0], Integer.parseInt(infos[1]));
        } catch (IOException e) {
            if (!(e instanceof FileNotFoundException))
                logger.error("can not load dc.", e);
        }

        return null;
    }

    @Override
    public void deleteAuthKey() {
        try {
            FileUtils.forceDelete(authKeyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteDc() {
        try {
            FileUtils.forceDelete(nearestDcFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveSession(@Nullable MTSession session) {

    }

    @Nullable
    @Override
    public MTSession loadSession() {
        return null;
    }
}
