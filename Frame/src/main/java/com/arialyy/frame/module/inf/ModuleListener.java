/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.arialyy.frame.module.inf;

/**
 * Created by AriaLyy on 2015/2/3.
 * Module监听
 */
public interface ModuleListener {
  /**
   * 无参的回调
   *
   * @param method 方法名
   */
  public void callback(String method);

  /**
   * 带参数的回调
   *
   * @param method 方法名
   * @param dataClassType 参数类型
   * @param data 数据
   */
  public void callback(String method, Class<?> dataClassType, Object data);

  /**
   * 统一接口的回调，回调接口为dataCallback
   *
   * @param result 返回码
   * @param data 回调数据
   */
  public void callback(int result, Object data);
}