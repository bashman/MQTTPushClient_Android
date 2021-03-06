/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity (tableName = "mqtt_messages",
        indices = {@Index(value = {"push_server_id", "mqtt_accont_id", "when", "seqno"}, unique = true, name = "message_idx")})
public class MqttMessage {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "push_server_id")
    private int pushServerID;
    @ColumnInfo(name = "mqtt_accont_id")
    private int mqttAccountID;

    private long when;
    private String topic;
    private byte[] payload;

    private int seqno;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPushServerID() {
        return pushServerID;
    }

    public void setPushServerID(int pushServerID) {
        this.pushServerID = pushServerID;
    }

    public int getMqttAccountID() {
        return mqttAccountID;
    }

    public void setMqttAccountID(int mqttAccountID) {
        this.mqttAccountID = mqttAccountID;
    }

    public long getWhen() {
        return when;
    }

    public void setWhen(long when) {
        this.when = when;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setPayload(byte[] payload) {
        this.payload = (payload == null ? new byte[0] : payload);
    }

    public byte[] getPayload() {
        return (payload == null ? new byte[0] : payload);
    }

    public void setSeqno(int seqNo) {
        seqno = seqNo;
    }

    public int getSeqno() {
        return seqno;
    }

    public final static String UPDATE_INTENT = "MQTT_MSG_UPDATE";
    public final static String DELETE_INTENT = "MQTT_MSG_DELETE";
    public final static String MSG_CNT_INTENT = "MSG_CNT_INTENT";

    public final static String ARG_CHANNELNAME = "MQTT_DEL_CHANNELNAME";
    public final static String ARG_PUSHSERVER_ADDR = "MQTT_MSG_UPDATE_PUSHSERVER";
    public final static String ARG_MQTT_ACCOUNT = "MQTT_MSG_UPDATE_ACC";
    public final static String ARG_CNT = "MQTT_MSG_CNT";
    public final static String ARG_IDS = "MQTT_MSG_UPDATE_IDS";

    /* values set by user filter script */
    @Ignore
    public Long backgroundColor;
    @Ignore
    public Long textColor;

    public static int MESSAGE_EXPIRE_DAYS = 30;
}
