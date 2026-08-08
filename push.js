/**
 * 一键推送到 GitHub
 * 用法: node push.js <GITHUB_TOKEN>
 */

const git = require('isomorphic-git');
const http = require('isomorphic-git/http/node');
const fs = require('fs');
const path = require('path');

const TOKEN = process.argv[2];
const DIR = __dirname;

if (!TOKEN) {
  console.error('❌ 请提供 Token');
  process.exit(1);
}

async function getUsername() {
  const res = await fetch('https://api.github.com/user', {
    headers: { Authorization: `Bearer ${TOKEN}`, Accept: 'application/vnd.github+json' },
  });
  if (!res.ok) { console.error('❌ Token 无效'); process.exit(1); }
  const data = await res.json();
  return data.login;
}

async function pushRepo(username) {
  const remoteUrl = `https://github.com/${username}/juexin-android-app.git`;

  console.log('📦 初始化 Git...');
  await git.init({ fs, dir: DIR, defaultBranch: 'main' });

  // 递归收集文件
  const fileList = [];
  function walk(dirPath) {
    const entries = fs.readdirSync(dirPath);
    for (const entry of entries) {
      if (entry === '.git' || entry === 'node_modules') continue;
      const full = path.join(dirPath, entry);
      const stat = fs.statSync(full);
      if (stat.isDirectory()) walk(full);
      else fileList.push(path.relative(DIR, full));
    }
  }
  walk(DIR);

  console.log(`📂 添加 ${fileList.length} 个文件...`);
  for (const file of fileList) {
    await git.add({ fs, dir: DIR, filepath: file });
  }

  console.log('💾 创建提交...');
  const sha = await git.commit({
    fs, dir: DIR,
    author: { name: 'JuexinDev', email: 'juexin@assistant.local' },
    message: '初始提交：觉心师父智能回复助手 Android 应用',
  });
  console.log(`  提交: ${sha.slice(0, 7)}`);

  console.log('🚀 推送到 GitHub...');
  
  // GitHub 支持 token 作为密码的 Basic 认证
  const onAuth = () => ({ username: TOKEN, password: 'x-oauth-basic' });

  try {
    await git.push({
      fs, http, dir: DIR,
      remote: 'origin',
      remoteRef: 'refs/heads/main',
      url: remoteUrl,
      onAuth,
    });
    console.log('✅ 推送成功 (main)');
  } catch (e) {
    console.log(`⚠️  main 分支: ${e.message}`);
    console.log('🔄 尝试 master 分支...');
    try {
      await git.push({
        fs, http, dir: DIR,
        remote: 'origin',
        remoteRef: 'refs/heads/master',
        url: remoteUrl,
        onAuth,
      });
      console.log('✅ 推送成功 (master)');
    } catch (e2) {
      throw new Error(`推送失败: ${e2.message}`);
    }
  }
}

(async () => {
  try {
    const username = await getUsername();
    console.log(`👤 ${username}`);

    await pushRepo(username);

    console.log('');
    console.log('🎉 完成！');
    console.log(`📋 仓库: https://github.com/${username}/juexin-android-app`);
    console.log(`🔧 Actions: https://github.com/${username}/juexin-android-app/actions`);
    console.log('⏳ 约5分钟后可在 Actions → 最新运行 → Artifacts 下载 APK');
  } catch (e) {
    console.error('❌ 失败:', e.message);
    process.exit(1);
  }
})();
