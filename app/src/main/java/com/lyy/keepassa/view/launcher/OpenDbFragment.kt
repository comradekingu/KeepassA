/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.launcher

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.transition.TransitionInflater
import com.keepassdroid.Database
import com.keepassdroid.utils.UriUtil
import com.lyy.keepassa.R
import com.lyy.keepassa.base.BaseApp
import com.lyy.keepassa.base.BaseFragment
import com.lyy.keepassa.databinding.FragmentOpenDbBinding
import com.lyy.keepassa.entity.DbHistoryRecord
import com.lyy.keepassa.util.HitUtil
import com.lyy.keepassa.util.KeepassAUtil
import com.lyy.keepassa.util.NotificationUtil
import com.lyy.keepassa.util.VibratorUtil
import com.lyy.keepassa.util.getArgument
import com.lyy.keepassa.util.takePermission
import com.lyy.keepassa.view.dialog.LoadingDialog
import com.lyy.keepassa.view.main.MainActivity
import com.tencent.bugly.crashreport.BuglyLog
import java.io.IOException

/**
 * 打开数据库页面
 */
class OpenDbFragment : BaseFragment<FragmentOpenDbBinding>(), View.OnClickListener {
  private lateinit var modlue: LauncherModule

  companion object {
    val FM_TAG = "OpenDbFragment"
    val REQ_CODE_FILE = 0xa1
  }

  private val openDbRecord by lazy {
    getArgument<DbHistoryRecord>("record")!!
  }
  private var showChangeDbBt: Boolean = true

  // 是否从填充页面进入
  private val openIsFromFill by lazy {
    getArgument("openIsFromFill") ?: false
  }

  private val loadingDialog by lazy {
    LoadingDialog(context)
  }

  /**
   * 快速解锁读取数据库回调
   */
  private val quickUnlockObserver by lazy {
    Observer<Pair<Boolean, String?>>{
      if (it.first) {
        openDb(it.second)
      }
    }
  }

  /**
   * 打开数据库完成的回调
   */
  private val openDbFinishedObserver by lazy {
    Observer<Database?> { db ->
      loadingDialog.dismiss()
      if (db == null) {
        HitUtil.toaskLong(getString(R.string.error_open_db))
        return@Observer
      }
      BaseApp.isLocked = false
      BaseApp.KDB = db
      Log.d(TAG, "打开数据库成功")
      loadingDialog.dismiss()
      if (openIsFromFill) {
        activity?.finish()
      } else {
        NotificationUtil.startDbOpenNotify(requireContext())
        MainActivity.startMainActivity(requireActivity())
      }
    }
  }

  override fun initData() {
    binding.fingerprint.visibility = View.GONE
    enterTransition = TransitionInflater.from(context)
        .inflateTransition(R.transition.slide_enter)
    exitTransition = TransitionInflater.from(context)
        .inflateTransition(R.transition.slide_exit)
    returnTransition = TransitionInflater.from(context)
        .inflateTransition(R.transition.slide_return)
    modlue = ViewModelProvider(this).get(LauncherModule::class.java)

    setDbName(openDbRecord)
    handleKeyUri(openDbRecord.getDbKeyUri())

    binding.cbKey.setOnCheckedChangeListener { _, isChecked ->
      if (isChecked) {
        if (TextUtils.isEmpty(openDbRecord.keyUri)
            || openDbRecord.keyUri.equals("null", ignoreCase = true)
        ) {
           KeepassAUtil.instance.openSysFileManager(this@OpenDbFragment, "*/*", REQ_CODE_FILE)
        }
        showKeyLayout()
      } else {
        openDbRecord.keyUri = ""
        hintKeyLayout()
      }
    }

    binding.open.setOnClickListener(this)
    binding.changeDb.setOnClickListener(this)
    binding.key.setOnClickListener(this)

    if (!showChangeDbBt) {
      binding.line.visibility = View.GONE
      binding.changeDb.visibility = View.GONE
    }

    binding.password.setOnEditorActionListener { _, actionId, _ ->
      // actionId 和android:imeOptions 属性要保持一致
      if (actionId == EditorInfo.IME_ACTION_DONE && !TextUtils.isEmpty(binding.password.text)) {
         KeepassAUtil.instance.toggleKeyBord(requireContext())
        openDb(
            binding.password.text.toString()
                .trim()
        )
        true
      } else {
        false
      }
    }

  }

  override fun onResume() {
    super.onResume()
    startHeadAnim()
    handleFingerprint()
  }

  /**
   * 处理指纹
   */
  @TargetApi(Build.VERSION_CODES.M) private fun handleFingerprint() {
    modlue.isNeedUseFingerprint(openDbRecord.localDbUri)
        .observe(this, Observer { needUse ->
          if (needUse) {
            binding.fingerprint.visibility = View.VISIBLE
            binding.fingerprint.playAnimation()
            showBiometricPrompt()
            binding.fingerprint.setOnClickListener {
              showBiometricPrompt()
            }
            return@Observer
          }
          binding.fingerprint.visibility = View.GONE
        })
  }

  fun hideFingerprint() {
    binding.fingerprint.visibility = View.GONE
  }

  /**
   * 显示验证指纹对话框
   */
  @SuppressLint("RestrictedApi") @TargetApi(Build.VERSION_CODES.M)
  private fun showBiometricPrompt() {
    if (!isAdded) {
      BuglyLog.w(TAG, "isAdd = false")
      return
    }
    if (BaseApp.KDB != null && !BaseApp.isLocked) {
      BuglyLog.d(TAG, "数据库已打开")
      return
    }

    modlue.getQuickUnlockRecord(openDbRecord, this).observe(this, quickUnlockObserver)
  }

  /**
   * 启动欢迎标题的动画
   */
  private fun startHeadAnim() {
    try {
      binding.anim.setAnimation(
          requireContext().assets.open("headAnim.json", AssetManager.ACCESS_STREAMING),
          "LottieCacheWebDav"
      )
      binding.anim.addAnimatorUpdateListener {
        if (binding.anim.frame == 40) {
          binding.anim.cancelAnimation()
          binding.anim.frame = 40
        }
      }
      binding.anim.addAnimatorListener(object : AnimatorListener {
        override fun onAnimationRepeat(animation: Animator?) {
        }

        override fun onAnimationEnd(animation: Animator?) {
        }

        override fun onAnimationCancel(animation: Animator?) {
          binding.head.visibility = View.VISIBLE
          ObjectAnimator.ofFloat(binding.head, "alpha", 0f, 1f)
              .setDuration(1000)
              .start()
        }

        override fun onAnimationStart(animation: Animator?) {
        }

      })
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  override fun onClick(v: View) {
    if ( KeepassAUtil.instance.isFastClick()) {
      return
    }
    when (v.id) {
      R.id.open -> {
        VibratorUtil.vibrator(300)
        openDb(binding.password.text.toString())
      }
      R.id.change_db -> {
        binding.cbKey.isChecked = false
        (activity as LauncherActivity).changeDb()
      }
      R.id.key -> {
         KeepassAUtil.instance.openSysFileManager(this@OpenDbFragment, "*/*", REQ_CODE_FILE)
      }
    }
  }

  /**
   * 打开数据库
   */
  private fun openDb(pass: String?) {
    val cache = pass ?: ""
    if (cache.isBlank() && openDbRecord.keyUri.isBlank()) {
      HitUtil.toaskShort(getString(R.string.error_input_pass_null))
      return
    }
    modlue.checkPassType(cache, openDbRecord.keyUri)

    loadingDialog.show()
    modlue.openDb(requireContext(), openDbRecord, cache).observe(this, openDbFinishedObserver)
  }

  /**
   * 更新数据
   * @param uri 如果打开文件的类型是AFS，该参数表示本地数据库的保存路径；如果是云端类型，表示的是云端路径
   */
  fun updateData(dbRecord: DbHistoryRecord) {
    openDbRecord.dbName = dbRecord.dbName
    openDbRecord.cloudDiskPath = dbRecord.cloudDiskPath
    openDbRecord.keyUri = dbRecord.keyUri
    openDbRecord.localDbUri = dbRecord.localDbUri
    openDbRecord.time = dbRecord.time
    openDbRecord.type = dbRecord.type
    openDbRecord.uid = dbRecord.uid
    Log.i(TAG, "更新数据，record = $dbRecord")
    setDbName(dbRecord)
    handleKeyUri( KeepassAUtil.instance.convertUri(dbRecord.keyUri))
  }

  private fun setDbName(dbRecord: DbHistoryRecord) {
    binding.db.text = Html.fromHtml(getString(R.string.db1, dbRecord.dbName))
    binding.db.setLeftIcon(
        resources.getDrawable(dbRecord.getDbPathType().icon, requireContext().theme)
    )
  }

  private fun handleKeyUri(keyUri: Uri?) {
    val uriStr = keyUri.toString()
    if (keyUri != null && !TextUtils.isEmpty(uriStr) && uriStr != "null") {
      binding.keyLayoutWrap.visibility = View.VISIBLE
      binding.cbKey.isChecked = true

      binding.key.text = getString(R.string.key1, UriUtil.getFileNameFromUri(BaseApp.APP, keyUri))
    } else {
      binding.keyLayoutWrap.visibility = View.GONE
    }
  }

  override fun setLayoutId(): Int {
    return R.layout.fragment_open_db
  }

  private fun showKeyLayout() {
    binding.keyLayoutWrap.visibility = View.VISIBLE
    binding.key.layoutParams.height = 0
    binding.key.visibility = View.VISIBLE
    val h = resources.getDimension(R.dimen.input_pass_key_h)
        .toInt()
    val anim = ValueAnimator.ofInt(0, h)
    anim.addUpdateListener { animation ->
      binding.key.layoutParams.height = animation.animatedValue as Int
      binding.key.requestLayout()
    }
    anim.duration = 400
    anim.interpolator = LinearInterpolator()
    anim.start()
  }

  private fun hintKeyLayout() {
    binding.keyLayoutWrap.visibility = View.VISIBLE
    val h = resources.getDimension(R.dimen.input_pass_key_h)
        .toInt()
    binding.key.layoutParams.height = h
    val anim = ValueAnimator.ofInt(h, 0)
    anim.addUpdateListener { animation ->
      binding.key.layoutParams.height = animation.animatedValue as Int
      binding.key.requestLayout()
    }
    anim.duration = 400
    anim.interpolator = LinearInterpolator()
    anim.start()

    anim.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationEnd(animation: Animator?) {
        binding.key.visibility = View.GONE
        binding.keyLayoutWrap.visibility = View.GONE
      }
    })
  }

  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQ_CODE_FILE) {
      if (resultCode == Activity.RESULT_OK) {
        if (data == null || data.data == null) {
          binding.cbKey.isChecked = false
          return
        }

        data.data?.takePermission()
        openDbRecord.keyUri = data.data.toString()
        binding.key.text = getString(
            R.string.key1, UriUtil.getFileNameFromUri(requireContext(), data.data)
        )
        return
      }

      if (binding.cbKey.isChecked) {
        binding.cbKey.isChecked = false
      }
    }
  }

}