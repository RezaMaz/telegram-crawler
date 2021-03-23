package ir.armansoft.telegram.gathering.integration;

import com.github.badoualy.telegram.tl.api.TLAbsChat;
import com.github.badoualy.telegram.tl.api.TLAbsMessage;
import com.github.badoualy.telegram.tl.api.TLUserFull;
import com.github.badoualy.telegram.tl.api.contacts.TLResolvedPeer;
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLChatFull;
import com.github.badoualy.telegram.tl.api.photos.TLAbsPhotos;
import ir.armansoft.telegram.gathering.indices.*;

public interface TelegramIntegration {
    //    General
    String getPhone();

    int getLastMessages(int lastMessageDate, CallBack<Integer, TLAbsDialogs> callBack);

    //    Channel
    void getChannelLastMessages(Channel channel, CallBack<Channel, TLAbsMessages> callBack);

    void getChannelArchiveMessages(Channel channel, CallBack<Channel, TLAbsMessages> callBack);

    void getChannelFullInformation(Channel channel, CallBack<Channel, TLChatFull> callBack);

    //  Hashcode
    void resolveHashcode(HashCode hashCode, CallBack<HashCode, TLAbsChat> callBack);

    //    Group
    void getGroupLastMessages(Group channel, CallBack<Group, TLAbsMessages> callBack);

    void getGroupArchiveMessages(Group channel, CallBack<Group, TLAbsMessages> callBack);

    void getGroupFullInformation(Group channel, CallBack<Group, TLChatFull> callBack);

    //    User
    void searchUsername(UserName userName, CallBack<UserName, TLResolvedPeer> callBack);

    void getUserFullInformation(User user, CallBack<User, TLUserFull> callBack);

    void getUserPhotos(User user, CallBack<User, TLAbsPhotos> callBack);

    void getUserArchivePhoto(User user, CallBack<User, TLAbsPhotos> callBack);

    //Message
    void getMessage(Channel channel, int id, CallBack<Channel, TLAbsMessage> callBack);
}
