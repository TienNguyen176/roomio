package com.tdc.nhom6.roomio.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tdc.nhom6.roomio.databinding.MainLayoutBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainLayoutBinding

    private val permissionsList: Array<String> by lazy {
        val list = mutableListOf<String>()

        list.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        list.toTypedArray()
    }

    // Mở Settings
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kiểm tra lại quyền
        handlePermissionAfterSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkOrRequestPermissions()
    }

    private fun checkOrRequestPermissions() {
        if (checkAllPermissions()) {
            doItfPermission()
        } else {
            requestPermissions(permissionsList, 9999)
        }
    }

    // Kiểm tra toàn bộ quyền
    private fun checkAllPermissions(): Boolean {
        for (p in permissionsList) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    // Xử lý khi từ Settings quay về
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handlePermissionAfterSettings() {
        val missing = getMissingPermissions()

        if (missing.isEmpty()) {
            doItfPermission()
            return
        }

        // Thông báo rõ ràng quyền nào chưa cấp
        showMissingPermissionDialog(missing)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != 9999) return

        val denied = getMissingPermissions()

        if (denied.isEmpty()) {
            doItfPermission()
            return
        }

        // Don't Ask Again → mở Settings
        for (p in denied) {
            if (!shouldShowRequestPermissionRationale(p)) {
                showGoToSettingsDialog(denied)
                return
            }
        }

        // Chỉ từ chối bình thường → yêu cầu lại
        requestPermissions(permissionsList, 9999)
    }

    // Lấy danh sách quyền chưa được cấp
    private fun getMissingPermissions(): List<String> {
        return permissionsList.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showGoToSettingsDialog(missingPermissions: List<String>) {
        val message = buildPermissionMessage(missingPermissions)

        AlertDialog.Builder(this)
            .setTitle("Yêu cầu cấp quyền")
            .setMessage("Ứng dụng cần các quyền sau để hoạt động:\n\n$message\n\nHãy cấp quyền trong Cài đặt.")
            .setCancelable(false)
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Thoát") { _, _ ->
                finish()
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showMissingPermissionDialog(missingPermissions: List<String>) {
        val message = buildPermissionMessage(missingPermissions)

        AlertDialog.Builder(this)
            .setTitle("Chưa đủ quyền")
            .setMessage("Bạn vẫn chưa cấp đủ quyền:\n\n$message\n\nHãy cấp đủ quyền để dùng ứng dụng.")
            .setCancelable(false)
            .setPositiveButton("Vào lại Cài đặt") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Thoát") { _, _ ->
                finish()
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun buildPermissionMessage(list: List<String>): String {
        val map = mapOf(
            Manifest.permission.CAMERA to "• Quyền Camera",
            Manifest.permission.READ_EXTERNAL_STORAGE to "• Quyền truy cập Ảnh",
            Manifest.permission.READ_MEDIA_IMAGES to "• Quyền truy cập Ảnh",
            Manifest.permission.POST_NOTIFICATIONS to "• Quyền gửi Thông báo"
        )

        return list.joinToString("\n") { map[it] ?: it }
    }

    private fun doItfPermission() {
        Toast.makeText(this, "Đã đủ quyền!", Toast.LENGTH_SHORT).show()
        // TODO: xử lý khi đã đủ quyền
    }
}
