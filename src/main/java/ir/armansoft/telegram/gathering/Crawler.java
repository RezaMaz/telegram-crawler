package ir.armansoft.telegram.gathering;

import com.github.badoualy.telegram.tl.api.TLAbsChat;
import com.github.badoualy.telegram.tl.api.TLUserFull;
import com.github.badoualy.telegram.tl.api.contacts.TLResolvedPeer;
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLChatFull;
import ir.armansoft.telegram.gathering.fetcher.ChannelFetcher;
import ir.armansoft.telegram.gathering.fetcher.HashCodeFetcher;
import ir.armansoft.telegram.gathering.fetcher.UserFetcher;
import ir.armansoft.telegram.gathering.fetcher.UsernameFetcher;
import ir.armansoft.telegram.gathering.indexer.impl.*;
import ir.armansoft.telegram.gathering.indices.Channel;
import ir.armansoft.telegram.gathering.indices.HashCode;
import ir.armansoft.telegram.gathering.indices.User;
import ir.armansoft.telegram.gathering.indices.UserName;
import ir.armansoft.telegram.gathering.integration.CallBack;
import ir.armansoft.telegram.gathering.integration.TelegramIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class Crawler {

    private final TelegramIntegration telegramIntegration;
    @Autowired
    private ChannelIndexer channelIndexer;
    @Autowired
    private HashCodeIndexer hashcodeIndexer;
    @Autowired
    private UsernameIndexer usernameIndexer;
    @Autowired
    private UserIndexer userIndexer;
    @Autowired
    private MessageIndexer messageIndexer;
    @Autowired
    private ChannelFetcher channelFetcher;
    @Autowired
    private HashCodeFetcher hashCodeFetcher;
    @Autowired
    private UserFetcher userFetcher;
    @Autowired
    private UsernameFetcher usernameFetcher;
    @Autowired
    private IndexerUtil indexerUtil;

    public Crawler(TelegramIntegration telegramIntegration) {
        this.telegramIntegration = telegramIntegration;
    }

    public int lastMessage(int lastMessageDate) {
        CallBack<Integer, TLAbsDialogs> callBack = (phone, input, output) -> {
            indexerUtil.index(phone, output.getChats());
            messageIndexer.index(output.getMessages());
            userIndexer.index(phone, output.getUsers());
        };
        return telegramIntegration.getLastMessages(lastMessageDate, callBack);
    }

    public String getPhone() {
        return telegramIntegration.getPhone();
    }

    public void indexChannelsForUpdateMessages(int size) {
        CallBack<Channel, TLAbsMessages> callBack = (phone, input, output) -> {
            if (output != null) {
                channelIndexer.index(phone, input, output, true);
            }
        };

        for (Channel channel : channelFetcher.findUpdateMessages(getPhone(), size)) {
            telegramIntegration.getChannelLastMessages(channel, callBack);
        }
    }

    public void indexChannelsForHistoryMessages(int size) {
        CallBack<Channel, TLAbsMessages> callBack = (phone, input, output) -> {
            if (output != null) {
                channelIndexer.index(phone, input, output, false);
            }
        };

        for (Channel channel : channelFetcher.findHistoryMessages(getPhone(), size)) {
            telegramIntegration.getChannelArchiveMessages(channel, callBack);
        }
    }

    public void searchUsername(int size) {
        CallBack<UserName, TLResolvedPeer> callBack = (phone, input, output) -> {
            if (output != null) {
                usernameIndexer.index(phone, input, output);
            }
        };

        for (UserName userName : usernameFetcher.findNotVerifiedUserNames(size)) {
            telegramIntegration.searchUsername(userName, callBack);
        }
    }

    public void resolveHashCodes(int size) {
        CallBack<HashCode, TLAbsChat> callBack = (phone, input, output) -> {
            if (output != null) {
                hashcodeIndexer.index(phone, input, output);
            }
        };
        for (HashCode hashCode : hashCodeFetcher.findNotVerifiedHashCodes(size)) {
            telegramIntegration.resolveHashcode(hashCode, callBack);
        }
    }

    public void indexUsersForFullInfo(int size) {
        CallBack<User, TLUserFull> callBack = (phone, input, output) -> {
            if (output != null) {
                userIndexer.index(input, output);
            }
        };

        for (User user : userFetcher.findUsersForFullInfo(getPhone(), size)) {
            telegramIntegration.getUserFullInformation(user, callBack);
        }
    }

    public void updateChannelsForFullInfo(int size) {
        CallBack<Channel, TLChatFull> callBack = (phone, input, output) -> {
            if (output != null) {
                channelIndexer.update(phone, input, output);
            }
        };

        for (Channel channel : channelFetcher.findUpdateChannelsForFullInfo(getPhone(), size)) {
            telegramIntegration.getChannelFullInformation(channel, callBack);
        }
    }
}
