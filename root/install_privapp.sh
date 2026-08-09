#!/system/bin/sh
# SeeMe root 保活：将应用安装为系统应用（/system/priv-app）
# 效果：开机即起、普通方式杀不掉、出厂级存活
#
# 用法：
#   1. adb install 普通 APK 并完成一次配置（或直接走下面步骤）
#   2. adb push app-debug.apk /sdcard/seeme.apk
#   3. adb shell sh /sdcard/install_privapp.sh
#   4. 重启手机
#
# 注意：Android 10+（system-as-root）remount /system 通常需要 Magisk，
#       若脚本提示 mount 失败，请改用 Magisk 模块方式（见 root/magisk-module/ 说明）。

APK=/sdcard/seeme.apk

echo "[1/4] 尝试以读写方式挂载 /system ..."
mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null || {
    echo "✗ 挂载失败：Android 10+ 需要 Magisk 或使用 boot 镜像方式"
    exit 1
}

echo "[2/4] 复制 APK 到 /system/priv-app/SeeMe ..."
mkdir -p /system/priv-app/SeeMe
cp "$APK" /system/priv-app/SeeMe/SeeMe.apk
chmod 644 /system/priv-app/SeeMe/SeeMe.apk
chown root:root /system/priv-app/SeeMe/SeeMe.apk

echo "[3/4] 卸载用户版本（若已安装） ..."
pm uninstall com.seeme.app 2>/dev/null

echo "[4/4] 重启 framework 让系统识别新系统应用 ..."
stop
start

echo "✔ 完成。建议 reboot 一次以确保 priv-app 生效。"
