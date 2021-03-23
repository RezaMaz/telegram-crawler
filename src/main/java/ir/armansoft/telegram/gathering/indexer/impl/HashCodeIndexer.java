package ir.armansoft.telegram.gathering.indexer.impl;

import com.github.badoualy.telegram.tl.api.TLAbsChat;
import com.github.badoualy.telegram.tl.api.TLChannel;
import com.github.badoualy.telegram.tl.api.TLChat;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.indices.Channel;
import ir.armansoft.telegram.gathering.indices.Group;
import ir.armansoft.telegram.gathering.indices.HashCode;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HashCodeIndexer {

    private final IndexerUtil indexerUtil;
    private final BulkRequestService bulkRequestService;

    @Autowired
    public HashCodeIndexer(IndexerUtil indexerUtil, BulkRequestService bulkRequestService) {
        this.indexerUtil = indexerUtil;
        this.bulkRequestService = bulkRequestService;
    }

    public void index(String phone, HashCode hashCode, TLAbsChat absChat) {
        UpdateRequest request = null;
        Gson gson = new Gson();
        JsonObject jsonObject = gson.toJsonTree(absChat).getAsJsonObject();
        if (absChat instanceof TLChannel) {
            request = indexerUtil.toUpdateRequest(Channel.INDEX, jsonObject);
            bulkRequestService.add(request);
            indexerUtil.removeErrorRequest(Channel.INDEX, absChat.getId(), phone);
            indexerUtil.makeAccessHashRequest(Channel.INDEX, jsonObject, phone);
        } else if (absChat instanceof TLChat) {
            request = indexerUtil.toUpdateRequest(Group.INDEX, jsonObject);
            bulkRequestService.add(request);
            indexerUtil.removeErrorRequest(Group.INDEX, absChat.getId(), phone);
            indexerUtil.makePhoneRequest(Group.INDEX, jsonObject.get("id").getAsString(), phone);
        }

        Map<String, Object> map = Maps.newHashMap();
        if (request != null) {
            map.put("type", request.type());
            map.put("reference", request.id());
            indexerUtil.makePhoneRequest(HashCode.INDEX, hashCode.getId(), phone);
            bulkRequestService.add(indexerUtil.toUpdateRequest(HashCode.INDEX, hashCode.getId(), map));
        } else {
            indexerUtil.makeErrorRequest(HashCode.INDEX, hashCode.getId(), phone);
        }
    }
}
