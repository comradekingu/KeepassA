/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lyy.keepassa.entity.CloudServiceInfo

@Dao
interface CloudServiceInfoDao {

  @Update
  suspend fun update(serviceInfo: CloudServiceInfo)

  @Query("SELECT * FROM CloudServiceInfo WHERE cloudPath=:uri")
  suspend fun queryServiceInfo(uri: String): CloudServiceInfo?

  @Insert
  suspend fun saveServiceInfo(serviceInfo: CloudServiceInfo)

  @Update
  suspend fun updateServiceInfo(serviceInfo: CloudServiceInfo)
}