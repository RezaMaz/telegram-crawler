package ir.armansoft.telegram.gathering.indexer.impl;

import com.github.badoualy.telegram.tl.api.TLAbsChannelParticipant;
import com.github.badoualy.telegram.tl.api.TLAbsMessage;
import com.github.badoualy.telegram.tl.api.TLMessage;
import com.github.badoualy.telegram.tl.api.channels.TLChannelParticipants;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLChatFull;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ir.armansoft.telegram.gathering.indexer.BulkRequestService;
import ir.armansoft.telegram.gathering.indices.Channel;
import ir.armansoft.telegram.gathering.indices.Gap;
import ir.armansoft.telegram.gathering.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ChannelIndexer {

    private static final long OneDayMillis = 24 * 60 * 60 * 1000L;
    private static final long SevenDayMillis = 7 * 24 * 60 * 60 * 1000L;
    private static final long MonthMillis = 30 * 24 * 60 * 60 * 1000L;
    private static final int CHANNEL_NEXT_FULL_DATE = 7;
    private static final int CHANNEL_NEXT_CRAWL_DATE = 60;

    private final IndexerUtil indexerUtil;
    private final UserIndexer userIndexer;
    private final MessageIndexer messageIndexer;
    private final MessageRepository messageRepository;
    private final BulkRequestService bulkRequestService;

    @Autowired
    public ChannelIndexer(
            IndexerUtil indexerUtil,
            UserIndexer userIndexer,
            MessageIndexer messageIndexer,
            MessageRepository messageRepository,
            BulkRequestService bulkRequestService
    ) {
        this.indexerUtil = indexerUtil;
        this.userIndexer = userIndexer;
        this.messageIndexer = messageIndexer;
        this.messageRepository = messageRepository;
        this.bulkRequestService = bulkRequestService;
    }

    public void update(String phone, Channel channel, TLChatFull chatFull) {
        JsonObject jsonObject = new Gson().toJsonTree(chatFull.getFullChat()).getAsJsonObject();

        Map<String, Object> map = Maps.newHashMap();
        map.put("id", channel.getId());
        map.put("fullDate", new Date().getTime());
        map.put("nextFullDate", DateUtils.addDays(new Date(), CHANNEL_NEXT_FULL_DATE).getTime());
        map.put("participantsCount", jsonObject.get("participantsCount").getAsInt());

        userIndexer.index(phone, chatFull.getUsers());
        indexerUtil.index(phone, chatFull.getChats());

        bulkRequestService.add(indexerUtil.toUpdateRequest(Channel.INDEX, jsonObject, map));
//        telegramBulkRequestService.add(indexerUtil.toIndexRequest(ANALYSIS, Channel.TYPE, new JsonObject(), map));
    }

    public void index(String phone, Channel channel, TLChannelParticipants output) {
        List<Integer> participants = Lists.newArrayList();
        userIndexer.index(phone, output.getUsers());
        for (TLAbsChannelParticipant participant : output.getParticipants()) {
            participants.add(participant.getUserId());
        }

        Map<String, Object> map = Maps.newHashMap();
        map.put("id", channel.getId());
        map.put("participants", participants);
        map.put("participantDate", new Date().getTime());
        //this will override participants every time!
        //finally store 200 users in end of list!
        map.put("participantsCount", output.getCount());

        bulkRequestService.add(indexerUtil.toUpdateRequest(Channel.INDEX, new JsonObject(), map));
    }

    //channel crawlDate set only when updated.
    public void index(String phone, Channel channel, TLAbsMessages messages, boolean update) {
        TLVector<TLAbsMessage> messageList = messages.getMessages();
        Map<String, Object> map = Maps.newHashMap();
        Map<String, Object> phoneMap = channel.getPhoneInfo();

        //get minMessage and maxMessage of list(on telegramId)
        TLMessage minMessage = null;
        TLMessage maxMessage = null;
        for (TLAbsMessage message : messageList) {
            if (message instanceof TLMessage) {
                minMessage = minMessage == null || minMessage.getId() > message.getId() ? (TLMessage) message : minMessage;
                maxMessage = maxMessage == null || maxMessage.getId() < message.getId() ? (TLMessage) message : maxMessage;
            }
        }

        //if all messageList is type of TLMessageService, minMessage is  null
        if (minMessage != null) {
            Long minMessageId = Long.valueOf(messageIndexer.generateMessageId(minMessage));
            boolean messageExists = messageRepository.existsById(minMessageId);
            if (update)
                if (!messageExists) { //if message don't exist in db, it's gap message
                    indexerUtil.addGapMessageId(Gap.INDEX, channel, minMessage.getId());
                    log.info(channel.getId() + " gaps created.");
                }
            if (!update) {
                if (!messageExists) {  //if message don't exist in db, it's gap message
                    indexerUtil.removeGapMessageId(Gap.INDEX, channel);
                    indexerUtil.addGapMessageId(Gap.INDEX, channel, minMessage.getId());
                    log.info(channel.getId() + " gaps replaced.");
                } else { //if exist remove gapMessageId
                    indexerUtil.removeGapMessageId(Gap.INDEX, channel);
                    log.info(channel.getId() + " gaps removed.");
                }
            }
        } else { //all msg is TLMessageService.
            if (update)
                map.put("error", "archive");
            else {
                indexerUtil.removeGapMessageId(Gap.INDEX, channel);
                log.info(channel.getId() + " gaps removed.");
            }
        }

        //if all messageList is type of  TLMessageService, maxMessage is  null
        if (maxMessage != null) {
            if (!phoneMap.containsKey("maxMessageDate") || maxMessage.getDate() * 1000L >
                    Long.parseLong(phoneMap.get("maxMessageDate").toString())) {
                map.put("maxMessageDate", maxMessage.getDate() * 1000L);
            }
        }

        if (update && !map.containsKey("error")) {
            //rating system
            //3(1d>10) 2(7d>10) 1(30d>10) 0(other)
            int[] count = new int[3];
            ArrayList<Integer> dateArray = new ArrayList<>();
            for (TLAbsMessage message : messageList) {
                if (message instanceof TLMessage) {
                    int date = ((TLMessage) message).getDate();
                    dateArray.add(date);
                    long fixDate = date * 1000L;
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    long zero_time = calendar.getTime().getTime(); //00:00 of day
                    if (fixDate <= zero_time && fixDate >= zero_time - OneDayMillis)
                        count[0]++;
                    if (fixDate <= zero_time && fixDate >= zero_time - SevenDayMillis)
                        count[1]++;
                    if (fixDate <= zero_time && fixDate >= zero_time - MonthMillis)
                        count[2]++;
                }
            }
            if (count[0] >= 10)
                map.put("rate", 3);
            else if (count[1] >= 10)
                map.put("rate", 2);
            else if (count[2] >= 10)
                map.put("rate", 1);
            else {
                if (count[2] == 0)
                    map.put("rate", -1);
                else
                    map.put("rate", 0);
            }

            if (map.get("rate").equals(-1))
                map.put("error", "inactive");
            else {
                //calculate averageView
//                telegramBulkRequestService.add(indexerUtil.toUpdateRequest(Channel.INDEX,
//                        new JsonObject(), calculationService.averagePostView(messageList, channel)));

                if (dateArray.size() == 0)
                    map.put("postRate", 0);
                else {
                    Collections.sort(dateArray);
                    int day_number = (dateArray.get(dateArray.size() - 1) - dateArray.get(0)) / (60 * 60 * 24);
                    if (day_number == 0)
                        map.put("postRate", 0);
                    else {
                        double pRate = (double) messageList.size() / day_number;
                        map.put("postRate", (double) Math.round(pRate * 10) / 10);
                    }
                }
                map.put("crawlDate", new Date().getTime());
                map.put("nextCrawlDate", DateUtils.addDays(new Date(), CHANNEL_NEXT_CRAWL_DATE).getTime());
            }
            Map<String, Object> langMap = Maps.newHashMap();
            langMap.put("id", channel.getId());
//            langMap.put("language", messageIndexer.languageDetection(messageList));
            bulkRequestService.add(indexerUtil.toUpdateRequest(Channel.INDEX, new JsonObject(), langMap));
        }
        messageIndexer.index(messageList);
        userIndexer.index(phone, messages.getUsers());
        indexerUtil.index(phone, messages.getChats());

        bulkRequestService.add(indexerUtil.toPhoneUpdateRequest(Channel.INDEX, channel.getId().toString(), phone, map));
    }
}
