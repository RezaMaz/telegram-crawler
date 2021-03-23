package ir.armansoft.telegram.gathering.indexer.impl;

import com.github.badoualy.telegram.tl.api.TLAbsChat;
import com.github.badoualy.telegram.tl.api.TLChannel;
import com.github.badoualy.telegram.tl.api.TLChat;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.indices.*;
import ir.armansoft.telegram.gathering.repository.GapRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class IndexerUtil {
    private final Client client;
    private final BulkRequestService bulkRequestService;
    private final GapRepository gapRepository;

    @Autowired
    public IndexerUtil(
            Client client,
            BulkRequestService bulkRequestService,
            GapRepository gapRepository
    ) {
        this.client = client;
        this.bulkRequestService = bulkRequestService;
        this.gapRepository = gapRepository;
    }

    public void makeTypeInaccessible(String phone, String index, Object id, Throwable error) {
        log.info("{}: {}#{} got error {}", phone, index, id, error.getMessage());
        if (error instanceof RpcErrorException) {
            RpcErrorException rpcError = (RpcErrorException) error;
            if (rpcError.getCode() == 420 && rpcError.getTag().contains("FLOOD_WAIT")) {
                int delay = Integer.parseInt(StringUtils.substringAfterLast(rpcError.getTag(), "_"));
                if (delay <= 20) {
                    log.info("{}#{} float weight error {} s and try again later.", index, id, delay);
                    log.info("sleep for {} s ...", delay);
                    try {
                        Thread.sleep(delay * 1000);
                    } catch (InterruptedException e) {
                        log.error("can not sleep", e);
                    }
                }
            } else if (rpcError.getTag().endsWith("CHANNEL_PUBLIC_GROUP_NA")) {
                makeErrorRequest(index, id.toString(), phone, error);
            } else if (rpcError.getTag().endsWith("CHANNEL_PRIVATE")) {
                makeErrorRequest(index, id.toString(), phone, error);
            } else if (rpcError.getTag().endsWith("INVITE_HASH_EXPIRED") || rpcError.getTag().endsWith("INVITE_HASH_INVALID")) {
                bulkRequestService.add(toUpdateRequest(index, id.toString(), Collections.singletonMap("error", error.getMessage())));
            } else if (rpcError.getTag().endsWith("CHANNEL_INVALID")) {
                bulkRequestService.add(client.prepareDelete(index, "_doc", id.toString()));
            } else if (rpcError.getTag().endsWith("CHANNELS_TOO_MUCH")) {
                log.error("CHANNELS_TOO_MUCH");
            } else if (rpcError.getTag().endsWith("PEER_ID_INVALID")) {
                log.error("PEER_ID_INVALID");
            } else if (rpcError.getTag().endsWith("USERNAME_NOT_OCCUPIED")) {
                makeErrorRequest(index, id.toString(), phone, error);
            } else if (rpcError.getTag().endsWith("USERNAME_INVALID")) {
                makeErrorRequest(index, id.toString(), phone, error);
            }
        }
    }

    UpdateRequest toUpdateRequest(String index, String id) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", id);
        return toUpdateRequest(index, jsonObject);
    }

    UpdateRequest toUpdateRequest(String index, String id, Map<String, Object> extraFields) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", id);
        return toUpdateRequest(index, jsonObject, extraFields);
    }

    UpdateRequest toUpdateRequest(String index, Object object) {
        JsonObject jsonObject = object instanceof JsonObject ? (JsonObject) object : new Gson().toJsonTree(object).getAsJsonObject();
        return toUpdateRequest(index, jsonObject, Collections.emptyMap());
    }

    UpdateRequest toUpdateRequest(String index, JsonObject jsonObject, Map<String, Object> extraFields) {
        if (index.equals(Channel.INDEX) || index.equals(Group.INDEX) || index.equals(Message.INDEX)) {
            fixDate("date", jsonObject);
        }

        if (index.equals(Message.INDEX)) {
            fixDate("fwdDate", jsonObject);
            if (jsonObject.has("media")) {
                JsonObject subJsonObject = null;
                JsonObject mediaJson = jsonObject.get("media").getAsJsonObject();
                if (mediaJson.has("audio")) {
                    subJsonObject = mediaJson.get("audio").getAsJsonObject();
                } else if (mediaJson.has("document")) {
                    subJsonObject = mediaJson.get("document").getAsJsonObject();
                } else if (mediaJson.has("photo")) {
                    subJsonObject = mediaJson.get("photo").getAsJsonObject();
                } else if (mediaJson.has("video")) {
                    subJsonObject = mediaJson.get("video").getAsJsonObject();
                } else if (mediaJson.has("webpage")) {
                    JsonObject webpageJson = mediaJson.get("webpage").getAsJsonObject();
                    if (webpageJson.has("photo")) {
                        subJsonObject = webpageJson.get("photo").getAsJsonObject();
                    }
                }
                if (subJsonObject != null) {
                    fixDate("date", subJsonObject);
                }
            }
        }

        if (jsonObject.has("status")) {
            fixDate("expires", jsonObject.get("status").getAsJsonObject());
            fixDate("wasOnline", jsonObject.get("status").getAsJsonObject());
        }

        for (Map.Entry<String, Object> entry : extraFields.entrySet())
            jsonObject.add(entry.getKey(), new Gson().toJsonTree(entry.getValue()));

        UpdateRequestBuilder updateRequestBuilder = client.prepareUpdate(index, "_doc", jsonObject.get("id").getAsString())
                .setDocAsUpsert(true)
                .setDoc(jsonObject.toString(), XContentType.JSON);

        return updateRequestBuilder.request();
    }

    public IndexRequest toIndexRequest(String index, String type, JsonObject jsonObject, Map<String, Object> extraFields) {
        for (Map.Entry<String, Object> entry : extraFields.entrySet())
            jsonObject.add(entry.getKey(), new Gson().toJsonTree(entry.getValue()));

        IndexRequestBuilder indexRequestBuilder = client.prepareIndex(index, type).setSource(jsonObject.toString(), XContentType.JSON);

        return indexRequestBuilder.request();
    }

    private void fixDate(String field, JsonObject jsonObject) {
        if (jsonObject.has(field)) {
            if (jsonObject.get(field).getAsString().length() <= 10)
                jsonObject.addProperty(field, jsonObject.remove(field).getAsLong() * 1000);
        }
    }

    void makeAccessHashRequest(String index, JsonObject jsonObject, String phone) {
        String id = jsonObject.get("id").getAsString();
        if (jsonObject.has("accessHash")) {
            String accessHash = jsonObject.remove("accessHash").getAsString();
            bulkRequestService.add(toPhoneUpdateRequestBuilder(index, id, phone, Collections.singletonMap("accessHash", accessHash))
                    .setUpsert(jsonObject.toString(), XContentType.JSON));
        } else {
            makePhoneRequest(index, id, phone);
        }
    }

    void makePhoneRequest(String index, String id, String phone) {
        bulkRequestService.add(toPhoneUpdateRequestBuilder(index, id, phone, Collections.emptyMap())
                .setScriptedUpsert(true));
    }

    void makeErrorRequest(String index, String id, String phone) {
        makeErrorRequest(index, id, phone, "User_Chat_Empty");
    }

    private void makeErrorRequest(String index, String id, String phone, Throwable e) {
        makeErrorRequest(index, id, phone, e.getMessage());
    }

    private void makeErrorRequest(String index, String id, String phone, String cause) {
        bulkRequestService.add(toPhoneUpdateRequestBuilder(index, id, phone, Collections.singletonMap("error", cause))
                .setScriptedUpsert(true));
    }

    void removeErrorRequest(String index, Object id, String phone) {
        bulkRequestService.add(client.prepareUpdate(index, "_doc", id.toString())
                .setScript(new Script(ScriptType.INLINE, "painless", "ctx._source.phoneInfo.remove(\"error\")}",
                        Collections.singletonMap("phone", phone))));
    }

    void addGapMessageId(String index, Resource chat, int gapMessageId) {
        bulkRequestService.add(
                client.prepareUpdate(chat.getIndex(), "_doc", chat.getId().toString())
                        .setScript(new Script(
                                "if (ctx._source.phoneInfo.gapNumber == null) { ctx._source.phoneInfo.gapNumber = 1 } " +
                                        "else { ctx._source.phoneInfo.gapNumber++ }")
                        ));
        bulkRequestService.add(
                client.prepareIndex(index, "_doc", chat.getId().toString() + gapMessageId)
                        .setSource("chatId", chat.getId(), "telegramId", gapMessageId)
        );
    }

    void removeGapMessageId(String index, Resource chat) {
        bulkRequestService.add(
                client.prepareUpdate(chat.getIndex(), "_doc", chat.getId().toString())
                        .setScript(new Script(
                                "ctx._source.phoneInfo.gapNumber--")
                        ));
        List<Gap> gapList = gapRepository.findGapByChatIdOrderByTelegramIdDesc((Integer) chat.getId());
        bulkRequestService.add(client.prepareDelete(index, "_doc", gapList.get(0).getId()));
    }

    UpdateRequest toPhoneUpdateRequest(String index, String id, String phone, Map<String, Object> map) {
        return toPhoneUpdateRequestBuilder(index, id, phone, map).request();
    }

    //update phoneInfo information
    private UpdateRequestBuilder toPhoneUpdateRequestBuilder(String index, String id, String phone, Map<String, Object> map) {
        StringBuilder phoneInfoParamsBuilder = new StringBuilder();
        for (String key : map.keySet()) {
            if (key.equals("error"))
                phoneInfoParamsBuilder.append("'").append(key).append("':'").append(map.get(key)).append("',");
            else
                phoneInfoParamsBuilder.append("'").append(key).append("':'").append(map.get(key)).append("',");
        }
        String phoneInfoParams = phoneInfoParamsBuilder.toString();
        phoneInfoParams = "[" + phoneInfoParams + "'number':'" + phone + "']";

        return client.prepareUpdate(index, "_doc", id)
                .setScript(new Script(
                        "def phoneInfoParams = " + phoneInfoParams + ";" +
                                "if (ctx._source.containsKey(\"phoneInfo\")){" +
                                "ctx._source.phoneInfo.putAll(phoneInfoParams)" +
                                "} else{" +
                                "ctx._source.phoneInfo = phoneInfoParams" +
                                "}")
                );
    }

    //index chats(groups and channels)
    public void index(String phone, TLVector<? extends TLAbsChat> chats) {
        Gson gson = new Gson();
        for (TLAbsChat absChat : chats) {
            JsonObject jsonObject = gson.toJsonTree(absChat).getAsJsonObject();
            if (absChat instanceof TLChannel) {
                bulkRequestService.add(toUpdateRequest(Channel.INDEX, jsonObject));
                makeAccessHashRequest(Channel.INDEX, jsonObject, phone);
            } else if (absChat instanceof TLChat) {
                bulkRequestService.add(toUpdateRequest(Group.INDEX, jsonObject));
                makePhoneRequest(Group.INDEX, String.valueOf(absChat.getId()), phone);
            }
        }
    }
}
