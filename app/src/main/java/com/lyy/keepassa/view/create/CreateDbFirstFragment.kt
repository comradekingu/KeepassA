/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.create

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.collection.arrayMapOf
import androidx.lifecycle.ViewModelProvider
import com.keepassdroid.utils.UriUtil
import com.lyy.keepassa.R
import com.lyy.keepassa.base.BaseFragment
import com.lyy.keepassa.databinding.FragmentCreateDbFirstBinding
import com.lyy.keepassa.event.DbPathEvent
import com.lyy.keepassa.util.HitUtil
import com.lyy.keepassa.util.KeepassAUtil
import com.lyy.keepassa.util.cloud.DbSynUtil
import com.lyy.keepassa.view.DbPathType
import com.lyy.keepassa.view.DbPathType.AFS
import com.lyy.keepassa.view.DbPathType.DROPBOX
import com.lyy.keepassa.view.DbPathType.UNKNOWN
import com.lyy.keepassa.view.DbPathType.WEBDAV
import com.lyy.keepassa.view.create.auth.AuthFlowFactory
import com.lyy.keepassa.view.create.auth.IAuthCallback
import com.lyy.keepassa.view.create.auth.IAuthFlow
import com.lyy.keepassa.view.create.auth.OnNextFinishCallback
import com.lyy.keepassa.view.dialog.MsgDialog
import com.lyy.keepassa.widget.BubbleTextView
import com.lyy.keepassa.widget.BubbleTextView.OnIconClickListener

/**
 * 创建数据库的第一步
 * 1、设置数据库保存类型
 * 2、设置数据库名
 */
class CreateDbFirstFragment : BaseFragment<FragmentCreateDbFirstBinding>() {

  private lateinit var module: CreateDbModule
  private lateinit var pathTypeDialog: PathTypeDialog
  private var authFlow: IAuthFlow? = null
  private var isAuthorized: Boolean = false
  private val flowMap = arrayMapOf<DbPathType, IAuthFlow>()

  override fun initData() {
    module = ViewModelProvider(requireActivity()).get(CreateDbModule::class.java)
    initView()
  }

  private fun initView() {
    setPathTypeInfo()
    showSaveTypeDialog()

    binding.pathType.setOnIconClickListener(object : OnIconClickListener {
      override fun onClick(
        view: BubbleTextView,
        index: Int
      ) {
        if (index == 2) {
          val dialog = MsgDialog.generate {
            msgTitle = this@CreateDbFirstFragment.getString(R.string.hint)
            msgContent = this@CreateDbFirstFragment.getString(R.string.help_create_db_path)
            showCancelBt = false
            build()
          }
          dialog.show()
        }
      }
    })

    // 设置键盘确定按钮属性
    binding.dbName.setOnEditorActionListener { _, actionId, _ ->
      if (!isAdded) {
        return@setOnEditorActionListener false
      }
      // actionId 和android:imeOptions 属性要保持一致
      if (actionId == EditorInfo.IME_ACTION_DONE && !TextUtils.isEmpty(binding.dbName.text)) {
        KeepassAUtil.instance.toggleKeyBord(requireContext())
//        showPathDialog()
        startNext()
        true
      } else {
        false
      }
    }
  }

  /**
   * 处理数据库名没有设置的情况
   */
  fun handleDbNameNull() {
    val hint = getString(R.string.error_db_name_null)

    binding.dbNameLayout.error = hint
    binding.dbName.requestFocus()
    HitUtil.toaskShort(hint)
    KeepassAUtil.instance.toggleKeyBord(requireContext())
  }

  /**
   * 和其它fragment共享的元素
   */
  fun getShareElement(): View {
    return binding.dbName
  }

  /**
   * 获取数据库名
   */
  fun getDbName(): String {
    module.dbName = binding.dbName.text.toString()
        .trim()
    return module.dbName
  }

  fun showSaveTypeDialog() {
    pathTypeDialog = PathTypeDialog(
        binding.dbName.text.toString()
            .trim()
    )
    pathTypeDialog.showNow(childFragmentManager, "PathDialog")
    pathTypeDialog.setOnDismissListener {
      if (module.dbPathType == UNKNOWN) {
        requireActivity().finishAfterTransition()
        return@setOnDismissListener
      }
      setPathTypeInfo()
      authFlow = flowMap[module.dbPathType]
      if (authFlow == null) {
        authFlow = AuthFlowFactory.getAuthFlow(module.dbPathType)
        flowMap[module.dbPathType] = authFlow
      }
      authFlow?.let {
        it.initContent(requireContext(), object : IAuthCallback {
          override fun callback(success: Boolean) {
            isAuthorized = success
            binding.dbName.requestFocus()
          }
        })
        it.startFlow()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    authFlow?.onResume()
  }

  /**
   * 流程结束
   */
  private fun finishFlow(pathEvent: DbPathEvent) {
    if (pathEvent.fileUri == null && pathEvent.dbPathType == AFS) {
      Log.e(TAG, "uri 获取失败")
      return
    }
    // 直接启动下一界面
    val startNextFragment = true

    when (pathEvent.dbPathType) {
      AFS -> {
        binding.dbNameLayout.visibility = View.VISIBLE
        module.dbUri = pathEvent.fileUri!!
        module.dbName = UriUtil.getFileNameFromUri(requireContext(), module.dbUri!!)
      }
      DROPBOX -> {
        binding.dbNameLayout.visibility = View.VISIBLE
        module.dbUri = DbSynUtil.getCloudDbTempPath(DROPBOX.name, getDbName())
        module.cloudPath = pathEvent.cloudDiskPath!!
        module.dbName = UriUtil.getFileNameFromUri(requireContext(), module.dbUri!!)
      }
      WEBDAV -> {
        binding.dbNameLayout.visibility = View.GONE
        val uri = Uri.parse(pathEvent.cloudDiskPath)
        module.dbName = uri.lastPathSegment ?: "unknown"
        module.dbUri = DbSynUtil.getCloudDbTempPath(WEBDAV.name, module.dbName)
        module.cloudPath = pathEvent.cloudDiskPath!!
      }
      else -> {
        throw IllegalArgumentException("不支持的类型: ${pathEvent.dbPathType.lable}")
      }
    }


    binding.dbName.setText(module.dbName)
    setPathTypeInfo()

    if (startNextFragment) {
      (activity as CreateDbActivity).startNextFragment()
    }
  }

  /**
   * 检查是否可以进入下一步
   */
  fun startNext(): Boolean {
    val temp = binding.dbName.text.toString()
        .trim()
    if (TextUtils.isEmpty(temp)) {
      HitUtil.toaskShort(getString(R.string.error_db_name_null))
      return false
    }

    authFlow?.doNext(this, temp, object : OnNextFinishCallback {
      override fun onFinish(event: DbPathEvent) {
        finishFlow(event)
      }
    })

    return true
  }

  /**
   * 设置文件路径类型提示
   */
  private fun setPathTypeInfo() {
    binding.pathType.text = module.dbPathType.lable
    binding.pathType.setLeftIcon(module.dbPathType.icon)
    setDbNameHint(module.dbPathType)
  }

  /**
   * 设置数据名输入提示
   */
  private fun setDbNameHint(dbPathType: DbPathType) {
    binding.dbNameLayout.helperText = getString(R.string.help_create_db)
    binding.dbNameLayout.hint = getString(R.string.db_name)
  }

  override fun setLayoutId(): Int {
    return R.layout.fragment_create_db_first
  }

  override fun onDestroy() {
    authFlow?.onDestroy()
    super.onDestroy()
  }

  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    super.onActivityResult(requestCode, resultCode, data)
    authFlow?.onActivityResult(requestCode, resultCode, data)
  }

}