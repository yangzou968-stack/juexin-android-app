#!/bin/bash
#
# 一键推送脚本 - 将觉心助手推送到GitHub并触发Actions构建APK
#
# 使用方法:
#   1. 先在GitHub创建仓库: https://github.com/new
#      - Repository name: juexin-android-app
#      - 设为 Private（推荐）或 Public
#      - 不要勾选任何初始化选项（README/LICENSE等都不选）
#
#   2. 创建完后，复制仓库URL（格式: https://github.com/你的用户名/juexin-android-app.git）
#
#   3. 运行此脚本:
#      bash push-to-github.sh https://github.com/你的用户名/juexin-android-app.git
#

set -e

REPO_URL="$1"

if [ -z "$REPO_URL" ]; then
    echo "❌ 请提供GitHub仓库URL"
    echo ""
    echo "使用方法:"
    echo "  bash push-to-github.sh https://github.com/你的用户名/juexin-android-app.git"
    echo ""
    echo "提示: 请先在 https://github.com/new 创建仓库"
    exit 1
fi

echo "🚀 觉心助手 - GitHub一键推送"
echo "================================"
echo ""

# 检查git
if ! command -v git &> /dev/null; then
    echo "❌ 未找到git，请先安装: xcode-select --install"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 初始化git（如果还没初始化）
if [ ! -d ".git" ]; then
    echo "📦 初始化Git仓库..."
    git init
    git config user.name "JuexinDev" || true
    git config user.email "juexin@assistant.local" || true
else
    echo "📦 Git仓库已存在"
fi

# 添加所有文件
echo "📁 添加项目文件..."
git add -A

# 检查是否有更改
if git diff --cached --quiet; then
    echo "⚠️  没有新的更改需要提交"
else
    echo "💾 提交更改..."
    git commit -m "初始版本: 觉心师父微信回复悬浮球助手

- 悬浮球始终显示在屏幕边缘
- 剪贴板监听，复制弟子消息自动弹出回复面板
- 7大场景模板匹配引擎（财运/婚姻/健康/子女/噩梦/抑郁/堕胎）
- 3种风格回复：慈悲共情 / 因果开示 / 行动指引
- 一键复制回微信粘贴发送
- GitHub Actions自动构建APK
- 最低支持 Android 8.0 (API 26)"
fi

# 设置远程仓库
echo ""
echo "🔗 配置远程仓库..."
if git remote | grep -q origin; then
    git remote set-url origin "$REPO_URL"
else
    git remote add origin "$REPO_URL"
fi

# 推送
echo "📤 推送到GitHub..."
git branch -M main
git push -u origin main

echo ""
echo "================================"
echo "✅ 推送成功！"
echo ""
echo "🔧 GitHub Actions 正在自动构建APK..."
echo "   查看进度: ${REPO_URL%.git}/actions"
echo ""
echo "📱 构建完成后（约5分钟）："
echo "   1. 打开 ${REPO_URL%.git}/actions"
echo "   2. 点击最新的 workflow run"
echo "   3. 页面底部 Artifacts 区域 → 下载 juexin-assistant-debug"
echo "   4. 解压zip → 得到 app-debug.apk"
echo "   5. 传到手机安装 → 授权悬浮窗权限 → 启动助手"
echo ""
echo "🙏 阿弥陀佛"
