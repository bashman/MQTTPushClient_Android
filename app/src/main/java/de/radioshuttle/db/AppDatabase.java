/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.db;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import android.content.Context;

@Database(entities = {MqttMessage.class, Code.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {

    public abstract MqttMessageDao mqttMessageDao();

    public static synchronized AppDatabase getInstance(final Context appContext) {

        if (db == null) {
            db = Room.databaseBuilder(appContext, AppDatabase.class, "mqtt_messages_db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return db;
    }

    private static AppDatabase db;

    private final static String TAG = AppDatabase.class.getSimpleName();
}

