package ir.armansoft.telegram.gathering.indexer.impl;

import com.github.badoualy.telegram.tl.api.TLAbsChat;
import com.github.badoualy.telegram.tl.api.TLAbsUser;
import com.github.badoualy.telegram.tl.api.TLChannel;
import com.github.badoualy.telegram.tl.api.TLChat;
import com.github.badoualy.telegram.tl.api.contacts.TLResolvedPeer;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.indices.UserName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UsernameIndexer {

    private final UserIndexer userIndexer;
    private final BulkRequestService bulkRequestService;
    private final IndexerUtil indexerUtil;

    @Autowired
    public UsernameIndexer(UserIndexer userIndexer, BulkRequestService bulkRequestService, IndexerUtil indexerUtil) {
        this.userIndexer = userIndexer;
        this.bulkRequestService = bulkRequestService;
        this.indexerUtil = indexerUtil;
    }

    //third parameter is the same as TLResolvedPeer resolvedPeer
    public void index(String phone, UserName userName, TLResolvedPeer tlFound) {
        index(phone, userName, tlFound.getChats(), tlFound.getUsers());
    }

    private void index(String phone, UserName userName, TLVector<TLAbsChat> chats, TLVector<TLAbsUser> users) {
        Map<String, Object> map = Maps.newHashMap();

        indexerUtil.index(phone, chats);
        userIndexer.index(phone, users);

        if (users.isEmpty() && chats.isEmpty()) {
            indexerUtil.makeErrorRequest(UserName.INDEX, userName.getId(), phone);
        } else {
            List<Integer> userIds = Lists.newArrayList();
            List<Integer> groupIds = Lists.newArrayList();
            List<Integer> channelIds = Lists.newArrayList();

            for (TLAbsUser user : users) {
                userIds.add(user.getId());
            }

            for (TLAbsChat absChat : chats) {
                if (absChat instanceof TLChannel) {
                    channelIds.add(absChat.getId());
                } else if (absChat instanceof TLChat) {
                    groupIds.add(absChat.getId());
                }
            }

            if (userIds.size() == 1) {
                map.put("userId", userIds.get(0));
            } else if (userIds.size() > 1) {
                map.put("userIds", userIds);
            }

            if (groupIds.size() == 1) {
                map.put("groupId", groupIds.get(0));
            } else if (groupIds.size() > 1) {
                map.put("groupIds", groupIds);
            }

            if (channelIds.size() == 1) {
                map.put("channelId", channelIds.get(0));
            } else if (channelIds.size() > 1) {
                map.put("channelIds", channelIds);
            }

            List<String> typeList = Lists.newArrayList();

            if (!userIds.isEmpty()) {
                typeList.add("user");
            }
            if (!channelIds.isEmpty()) {
                typeList.add("channel");
            }
            if (!groupIds.isEmpty()) {
                typeList.add("group");
            }

            if (typeList.size() == 1) {
                map.put("type", typeList.get(0));
            } else {
                map.put("types", typeList);
            }
            indexerUtil.makePhoneRequest(UserName.INDEX, userName.getId(), phone);
            bulkRequestService.add(indexerUtil.toUpdateRequest(UserName.INDEX, userName.getId(), map));
        }
    }
}
