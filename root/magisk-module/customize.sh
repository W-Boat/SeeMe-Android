#!/system/bin/sh
# 模块安装脚本：把 APK 放到 system/priv-app 下
MODDIR=${0%/*}

mkdir -p "$MODDIR/system/priv-app/SeeMe"
# 模块目录里预置 APK（构建模块时放入），这里仅确保权限
chmod 644 "$MODDIR/system/priv-app/SeeMe/SeeMe.apk" 2>/dev/null || true
