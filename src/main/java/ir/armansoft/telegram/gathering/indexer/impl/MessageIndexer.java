package ir.armansoft.telegram.gathering.indexer.impl;

import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.twitter.Extractor;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.indices.HashCode;
import ir.armansoft.telegram.gathering.indices.Message;
import ir.armansoft.telegram.gathering.indices.UserName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MessageIndexer {
    public final Extractor extractor;
    private final IndexerUtil indexerUtil;
    private final BulkRequestService bulkRequestService;

    @Autowired
    public MessageIndexer(Extractor extractor, IndexerUtil indexerUtil, BulkRequestService bulkRequestService) {
        this.extractor = extractor;
        this.indexerUtil = indexerUtil;
        this.bulkRequestService = bulkRequestService;
    }

    public void indexJoinLinks(TLVector<? extends TLAbsMessage> messages) {
        Set<String> matches = Sets.newHashSet();
        for (TLAbsMessage absMessage : messages) {
            if (absMessage instanceof TLMessage) {
                Matcher firstPattern = Pattern.compile("telegram.me/joinchat/([\\S]+)").matcher(getTLMessageText(absMessage));
                while (firstPattern.find()) {
                    matches.add(firstPattern.group(1));
                }

                Matcher secondPattern = Pattern.compile("t.me/joinchat/([\\S]+)").matcher(getTLMessageText(absMessage));
                while (secondPattern.find()) {
                    matches.add(secondPattern.group(1));
                }
            }
        }

        for (String hashcode : matches) {
            bulkRequestService.add(indexerUtil.toUpdateRequest(HashCode.INDEX, hashcode));
        }
    }

    //get text of TLMessage of any type(message & media(10 type)
    private String getTLMessageText(TLAbsMessage absMessage) {
        String message = ((TLMessage) absMessage).getMessage();
        if (message.isEmpty()) {
            TLAbsMessageMedia media = ((TLMessage) absMessage).getMedia();
            if (media instanceof TLMessageMediaDocument) { //video and audio and image/webp(sticker) is in this section
                message = ((TLMessageMediaDocument) media).getCaption();
            } else if (media instanceof TLMessageMediaPhoto) {
                message = ((TLMessageMediaPhoto) media).getCaption();
            } else if (media instanceof TLMessageMediaVenue) {
                message = ((TLMessageMediaVenue) media).getTitle() + ((TLMessageMediaVenue) media).getAddress();
            }
        }
        return message;
    }

    public void indexUsernames(TLVector<? extends TLAbsMessage> messages) {
        Set<String> matches = Sets.newHashSet();
        for (TLAbsMessage absMessage : messages) {
            if (absMessage instanceof TLMessage) {
                List<String> list = extractor.extractMentionedScreennames(getTLMessageText(absMessage));
                matches.addAll(list);
            }
        }

        for (String username : matches) {
            bulkRequestService.add(indexerUtil.toUpdateRequest(UserName.INDEX, username));
        }
    }

    public void index(TLVector<? extends TLAbsMessage> messages) {
        Gson gson = new Gson();
        for (TLAbsMessage absMessage : messages) {
            if (absMessage instanceof TLMessage) {
                String messageId = generateMessageId((TLMessage) absMessage);
                JsonObject jsonObject = gson.toJsonTree(absMessage).getAsJsonObject();
                jsonObject.addProperty("message", getTLMessageText(absMessage));
                jsonObject.remove("entities");
                jsonObject.remove("media");
                if (jsonObject.has("media") &&
                        jsonObject.getAsJsonObject("media").has("webpage") &&
                        jsonObject.getAsJsonObject("media").getAsJsonObject("webpage").has("cachedPage"))
                    jsonObject.getAsJsonObject("media").getAsJsonObject("webpage").remove("cachedPage");

                String telegramId = jsonObject.remove("id").getAsString();
                jsonObject.addProperty("id", messageId);
                jsonObject.addProperty("telegramId", telegramId);

                bulkRequestService.add(indexerUtil.toUpdateRequest(Message.INDEX, jsonObject));
            }
        }
        indexJoinLinks(messages);
        indexUsernames(messages);
    }

    public void index(TLAbsMessage message) {
        Gson gson = new Gson();
        if (message instanceof TLMessage) {
            String messageId = generateMessageId((TLMessage) message);
            JsonObject jsonObject = gson.toJsonTree(message).getAsJsonObject();
            if (jsonObject.has("media") &&
                    jsonObject.getAsJsonObject("media").has("webpage") &&
                    jsonObject.getAsJsonObject("media").getAsJsonObject("webpage").has("cachedPage"))
                jsonObject.getAsJsonObject("media").getAsJsonObject("webpage").remove("cachedPage");

            String telegramId = jsonObject.remove("id").getAsString();
            jsonObject.addProperty("id", messageId + new Date().getTime());
            jsonObject.addProperty("telegramId", telegramId);
            jsonObject.addProperty("crawlDate", new Date().getTime());
//            telegramBulkRequestService.add(indexerUtil.toUpdateRequest(ANALYSIS, Message.TYPE, jsonObject));
        }
    }

    public void index(Message message, double weight) {
        Map<String, Object> map = Maps.newHashMap();
        map.put("weight", weight);
        bulkRequestService.add(indexerUtil.toUpdateRequest(Message.INDEX, message.getId().toString(), map));
    }

    public String generateMessageId(TLMessage tlMessage) {
        String chatId = "";
        String messageId = String.valueOf(tlMessage.getId());

        if (tlMessage.getToId() instanceof TLPeerUser) {
            chatId = String.valueOf((((TLPeerUser) tlMessage.getToId()).getUserId()));
        } else if (tlMessage.getToId() instanceof TLPeerChannel) {
            chatId = String.valueOf(((TLPeerChannel) tlMessage.getToId()).getChannelId());
        } else if (tlMessage.getToId() instanceof TLPeerChat) {
            chatId = String.valueOf(((TLPeerChat) tlMessage.getToId()).getChatId());
        }

        int remain = 18 - chatId.length() - messageId.length();

        String date = StringUtils.substring(String.valueOf(tlMessage.getDate()), 0, -2);


        if (date.length() < remain) {
            date = StringUtils.repeat("0", remain - date.length()) + date;
        } else if (date.length() > remain) {
            date = date.substring(date.length() - remain);
        }

        return chatId + date + messageId;
    }
}
