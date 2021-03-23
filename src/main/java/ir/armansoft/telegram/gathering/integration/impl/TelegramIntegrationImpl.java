package ir.armansoft.telegram.gathering.integration.impl;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.api.contacts.TLResolvedPeer;
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLChatFull;
import com.github.badoualy.telegram.tl.api.photos.TLAbsPhotos;
import com.github.badoualy.telegram.tl.api.storage.*;
import com.github.badoualy.telegram.tl.api.upload.TLFile;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import ir.armansoft.telegram.gathering.fetcher.FetcherUtil;
import ir.armansoft.telegram.gathering.indexer.impl.IndexerUtil;
import ir.armansoft.telegram.gathering.indices.*;
import ir.armansoft.telegram.gathering.integration.CallBack;
import ir.armansoft.telegram.gathering.integration.TelegramIntegration;
import ir.armansoft.telegram.gathering.repository.GapRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class TelegramIntegrationImpl implements TelegramIntegration {

    private final TelegramClient telegramClient;
    private final String phone;
    @Autowired
    private IndexerUtil indexerUtil;
    @Autowired
    private FetcherUtil fetcherUtil;
    @Autowired
    private GapRepository gapRepository;

    public TelegramIntegrationImpl(String phone, TelegramClient telegramClient) {
        this.phone = phone;
        this.telegramClient = telegramClient;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    @Override
    public void searchUsername(UserName userName, CallBack<UserName, TLResolvedPeer> callBack) {
        try {
            //in some cases no chats or username return from this method.
//            callBack.call(phone, userName, client.contactsSearch(userName.getId(), Integer.MAX_VALUE));
            //client.contactsResolveUsername is subsequent of contactsSearch
            callBack.call(phone, userName, telegramClient.contactsResolveUsername(userName.getId()));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, UserName.INDEX, userName.getId(), e);
        }
    }

    @Override
    public int getLastMessages(int lastMessageDate, CallBack<Integer, TLAbsDialogs> callBack) {
        int limit = 100; //max number of dialogue we want to read in one req.
        int minMessageDate = 0;
        int maxMessageDate = 0;

        try {
            TLAbsDialogs absDialogs;
            do {
                // Returns the current user dialog list.
                // in dialog list when you go top to bottom, message date come new to old.
                absDialogs = telegramClient.messagesGetDialogs(true, minMessageDate, 0, new TLInputPeerEmpty(), limit);
                if (absDialogs != null) {
                    for (TLAbsMessage absMessage : absDialogs.getMessages()) {
                        if (absMessage instanceof TLMessage) {
                            TLMessage message = ((TLMessage) absMessage);
                            if (minMessageDate == 0 || minMessageDate > message.getDate()) { //min
                                minMessageDate = message.getDate();
                            }
                            if (maxMessageDate < message.getDate()) { //max
                                maxMessageDate = message.getDate();
                            }
                        }
                        //messageService(like taha ahmadi left the group!) has date.
                        if (absMessage instanceof TLMessageService) {
                            TLMessageService message = ((TLMessageService) absMessage);
                            if (minMessageDate == 0 || minMessageDate > message.getDate()) { //min
                                minMessageDate = message.getDate();
                            }
                            if (maxMessageDate < message.getDate()) { //max
                                maxMessageDate = message.getDate();
                            }
                        }
                    }
                    callBack.call(phone, minMessageDate, absDialogs);
                }
            } while (minMessageDate > lastMessageDate && absDialogs != null && absDialogs.getMessages().size() == limit);
        } catch (IOException | RpcErrorException e) {
            log.error("can't get last messages.", e.getMessage());
        }

        return maxMessageDate;
    }

    //get full information of channel
    @Override
    public void getChannelFullInformation(Channel channel, CallBack<Channel, TLChatFull> callBack) {
        try {
            TLInputChannel inputChannel = new TLInputChannel(channel.getId(), getAccessHash(channel.getPhoneInfo()));
            callBack.call(phone, channel, telegramClient.channelsGetFullChannel(inputChannel));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Channel.INDEX, channel.getId(), e);
        }
    }

    //update channel messages
    @Override
    public void getChannelLastMessages(Channel channel, CallBack<Channel, TLAbsMessages> callBack) {
        try {
            TLInputPeerChannel peerChannel = new TLInputPeerChannel(channel.getId(), getAccessHash(channel.getPhoneInfo()));
            callBack.call(phone, channel,
                    telegramClient.messagesGetHistory(peerChannel, 0, 0, 0, 100, Integer.MAX_VALUE, Integer.MIN_VALUE));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Channel.INDEX, channel.getId(), e);
        }
    }

    @Override
    public void getChannelArchiveMessages(Channel channel, CallBack<Channel, TLAbsMessages> callBack) {
        try {
            Integer gapMessageId = getGapMessageId(channel.getId());
            TLInputPeerChannel peerChannel = new TLInputPeerChannel(channel.getId(), getAccessHash(channel.getPhoneInfo()));
            callBack.call(phone, channel,
                    telegramClient.messagesGetHistory(peerChannel, gapMessageId, 0, 0, 20, Integer.MAX_VALUE, Integer.MIN_VALUE));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Channel.INDEX, channel.getId(), e);
        }
    }

    @Override
    public void getMessage(Channel channel, int id, CallBack<Channel, TLAbsMessage> callBack) {
        try {
            TLInputPeerChannel peerChannel = new TLInputPeerChannel(channel.getId(), getAccessHash(channel.getPhoneInfo()));
            //messagesGetHistory return message with (id-1), so we plus id one.
            callBack.call(phone, channel,
                    telegramClient.messagesGetHistory(peerChannel, id + 1, 0, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE)
                            .getMessages().get(0));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Channel.INDEX, channel.getId(), e);
        }
    }

    //replace getMinMessageId
    private Integer getGapMessageId(int chatId) {
        List<Gap> gapList = gapRepository.findGapByChatIdOrderByTelegramIdDesc(chatId);
        if (gapList.size() == 0)
            return 0;
        else
            return gapList.get(0).getTelegramId();
    }

    private long getAccessHash(Map<String, Object> phoneInfo) {
        if (phoneInfo.get("number").equals(getPhone())) {
            return Long.parseLong(phoneInfo.get("accessHash").toString());
        }
        return 0;
    }

    @Override
    public void resolveHashcode(HashCode hashCode, CallBack<HashCode, TLAbsChat> callBack) {
        try {
            TLAbsChatInvite absChatInvite = telegramClient.messagesCheckChatInvite(hashCode.getId());
            if (absChatInvite instanceof TLChatInviteAlready) {
                callBack.call(phone, hashCode, ((TLChatInviteAlready) absChatInvite).getChat());
            } else {
                TLAbsUpdates absUpdates = telegramClient.messagesImportChatInvite(hashCode.getId());
                if (absUpdates instanceof TLUpdates) {
                    callBack.call(phone, hashCode, ((TLUpdates) absUpdates).getChats().get(0));
                }
            }
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, HashCode.INDEX, hashCode.getId(), e);
        }
    }

    @Override
    public void getGroupFullInformation(Group group, CallBack<Group, TLChatFull> callBack) {
        try {
            callBack.call(phone, group, telegramClient.messagesGetFullChat(group.getId()));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Group.INDEX, group.getId(), e);
        }
    }

    @Override
    public void getGroupLastMessages(Group group, CallBack<Group, TLAbsMessages> callBack) {
        try {
            TLInputPeerChat peerChannel = new TLInputPeerChat(group.getId());
            callBack.call(phone, group, telegramClient.messagesGetHistory(peerChannel, 0, 0, 0, 100, Integer.MAX_VALUE, Integer.MIN_VALUE));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Group.INDEX, group.getId(), e);
        }
    }

    @Override
    public void getGroupArchiveMessages(Group group, CallBack<Group, TLAbsMessages> callBack) {
        try {
            Integer gapMessageId = getGapMessageId(group.getId());
            TLInputPeerChat peerChannel = new TLInputPeerChat(group.getId());
            callBack.call(phone, group, telegramClient.messagesGetHistory(peerChannel, gapMessageId, 0, 0, 100, Integer.MAX_VALUE, Integer.MIN_VALUE));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, Group.INDEX, group.getId(), e);
        }
    }

    @Override
    public void getUserFullInformation(User user, CallBack<User, TLUserFull> callBack) {
        try {
            callBack.call(phone, user, telegramClient.usersGetFullUser(new TLInputUser(user.getId(), getAccessHash(user.getPhoneInfo()))));
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, User.INDEX, user.getId(), e);
        }
    }

    //get total user photo recursively(we don't need getUserArchivePhoto)
    @Override
    public void getUserPhotos(User user, CallBack<User, TLAbsPhotos> callBack) {
        try {
            long minPhotoId = 0;
            TLAbsPhotos tlAbsPhotos;
            do {
                tlAbsPhotos = telegramClient.photosGetUserPhotos(new TLInputUser(user.getId(),
                        getAccessHash(user.getPhoneInfo())), 0, minPhotoId, Integer.MAX_VALUE);
                if (!tlAbsPhotos.getPhotos().isEmpty())
                    minPhotoId = tlAbsPhotos.getPhotos().get(tlAbsPhotos.getPhotos().size() - 1).getId();
                downloadUserPhotos(user, tlAbsPhotos);
                callBack.call(phone, user, tlAbsPhotos);
            }
            while (!tlAbsPhotos.getPhotos().isEmpty() && tlAbsPhotos.getPhotos().size() != 0);
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, User.INDEX, user.getId(), e);
        }
    }

    private void downloadUserPhotos(User user, TLAbsPhotos tlAbsPhotos) {
        for (TLAbsPhoto tlAbsPhoto : tlAbsPhotos.getPhotos()) {
            TLVector<TLAbsPhotoSize> sizes = tlAbsPhoto.getAsPhoto().getSizes();
            try {
                //limit is multiplier of 1Kb
                TLPhotoSize photoSize = (TLPhotoSize) sizes.get(sizes.size() - 1);
                TLFile tlFile = (TLFile) telegramClient.uploadGetFile(
                        new TLInputFileLocation(photoSize.getLocation().getVolumeId(),
                                photoSize.getLocation().getLocalId(), photoSize.getLocation().getSecret())
                        , 0, 1024 * 1024); //1Mb limit for photos
                File output = FileUtils.getFile("user", user.getId().toString(), tlAbsPhoto.getId() + getExtension(tlFile.getType()));
                FileUtils.writeByteArrayToFile(output, tlFile.getBytes().getData());
                log.info("file {} saved successfully.", output.getPath());
            } catch (Throwable e) {
                log.error("can not download photo#{}", tlAbsPhoto.getId(), e);
            }
        }
    }

    private String getExtension(TLAbsFileType type) {
        if (type instanceof TLFileJpeg)
            return ".jpg";
        else if (type instanceof TLFileGif)
            return ".gif";
        else if (type instanceof TLFilePng)
            return ".png";
        else if (type instanceof TLFilePdf)
            return ".pdf";
        else if (type instanceof TLFileMp3)
            return ".mp3";
        else if (type instanceof TLFileMp4)
            return ".mp4";
        else if (type instanceof TLFileMov)
            return ".mov";
        else
            return ".xxx";
    }

    @Override
    public void getUserArchivePhoto(User user, CallBack<User, TLAbsPhotos> callBack) {
        try {
            int minPhotoId = user.getMinPhotoId() == null ? 0 : user.getMinPhotoId().intValue();
            TLAbsPhotos tlAbsPhotos = telegramClient.photosGetUserPhotos(new TLInputUser(user.getId(), getAccessHash(user.getPhoneInfo())), 0, minPhotoId, Integer.MAX_VALUE);
            downloadUserPhotos(user, tlAbsPhotos);
            callBack.call(phone, user, tlAbsPhotos);
        } catch (Throwable e) {
            indexerUtil.makeTypeInaccessible(phone, User.INDEX, user.getId(), e);
        }
    }
}
