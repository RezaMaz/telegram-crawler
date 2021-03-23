package ir.armansoft.telegram.gathering.indexer.impl;

import com.github.badoualy.telegram.tl.api.TLAbsPhoto;
import com.github.badoualy.telegram.tl.api.TLAbsUser;
import com.github.badoualy.telegram.tl.api.TLUser;
import com.github.badoualy.telegram.tl.api.TLUserFull;
import com.github.badoualy.telegram.tl.api.photos.TLAbsPhotos;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.indices.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
public class UserIndexer {
    private final IndexerUtil indexerUtil;

    private final BulkRequestService bulkRequestService;

    @Autowired
    public UserIndexer(IndexerUtil indexerUtil, BulkRequestService bulkRequestService) {
        this.indexerUtil = indexerUtil;
        this.bulkRequestService = bulkRequestService;
    }

    public void index(String phone, TLVector<? extends TLAbsUser> users) {
        Gson gson = new Gson();
        for (TLAbsUser absUser : users) {
            JsonObject jsonObject = gson.toJsonTree(absUser).getAsJsonObject();
            if (absUser instanceof TLUser) {
                bulkRequestService.add(indexerUtil.toUpdateRequest(User.INDEX, jsonObject));
                indexerUtil.makeAccessHashRequest(User.INDEX, jsonObject, phone);
            }
        }
    }

    public void index(User user, TLUserFull userFull) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("id", user.getId());
        map.put("fullDate", new Date().getTime());
        JsonObject jsonObject = new Gson().toJsonTree(userFull).getAsJsonObject();
        jsonObject.remove("user"); //user basic data already saved in db.
        jsonObject.remove("link"); //contains user data!
        bulkRequestService.add(indexerUtil.toUpdateRequest(User.INDEX, jsonObject, map));
    }

    //index user photos
    public void index(String phone, User user, TLAbsPhotos output) {
        Map<String, Object> map = Maps.newHashMap();
        TLVector<TLAbsPhoto> photos = output.getPhotos();
        if (photos.isEmpty()) {
            map.put("visitLastPhoto", true);
        } else {
            map.put("minPhotoId", photos.get(photos.size() - 1).getId());
        }
        bulkRequestService.add(indexerUtil.toUpdateRequest(User.INDEX, user.getId().toString(), map));
    }
}
