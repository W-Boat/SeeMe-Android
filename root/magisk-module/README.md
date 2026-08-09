# SeeMe Magisk 模块说明
#
# install_privapp.sh 在 Android 10+（system-as-root）上 remount /system 常会失败，
# 此时用 Magisk 模块把 APK 塞进系统分区最稳。

## 用法
# 1. 在 Magisk 里安装本模块 zip（root/magisk-module-seeme.zip，构建方式如下）
# 2. 重启即生效：SeeMe 变成系统应用，开机自启、不可被杀

## 手工构建模块 zip
# 在 root/magisk-module/ 目录下执行：
#   zip -r ../magisk-module-seeme.zip . -x "*.DS_Store"

## 更新 APK
# 替换 root/magisk-module/system/priv-app/SeeMe/SeeMe.apk 后重新打包安装并重启。
