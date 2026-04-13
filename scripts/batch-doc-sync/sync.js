#!/usr/bin/env node

/**
 * 批量文档同步脚本
 * 用法: node sync.js [--type <类型>] [--all] [--incremental]
 * 
 * 类型: keyword_cards, event_cards, achievements, ai_prompts
 */

const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

// 配置
const CONFIG = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '3306'),
  user: process.env.DB_USER || 'appuser',
  password: process.env.DB_PASSWORD || 'apppassword',
  database: process.env.DB_NAME || 'arbitrary_gate',
};

// 同步类型配置
const SYNC_TYPES = {
  keyword_cards: {
    table: 'keyword_card',
    idField: 'id',
    file: path.join(__dirname, '../../data/keyword_cards.json'),
    fallbackFile: path.join(__dirname, '../../data/keyword_cards_sample.json'),
    validate: validateKeywordCard,
    insert: insertKeywordCard,
  },
  event_cards: {
    table: 'event_card',
    idField: 'id',
    file: path.join(__dirname, '../../data/event_cards.json'),
    fallbackFile: path.join(__dirname, '../../data/event_cards_sample.json'),
    validate: validateEventCard,
    insert: insertEventCard,
  },
  achievements: {
    table: 'achievement',
    idField: 'id',
    file: path.join(__dirname, '../../data/achievements.json'),
    fallbackFile: path.join(__dirname, '../../data/achievements_sample.json'),
    validate: validateAchievement,
    insert: insertAchievement,
  },
};

const args = process.argv.slice(2);
const mode = args.includes('--incremental') ? 'incremental' : 'full';
const syncAll = args.includes('--all');

let targetType = null;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--type' && args[i + 1]) {
    targetType = args[i + 1];
  }
}

async function main() {
  console.log('📦 批量文档同步工具\n');
  console.log(`模式: ${mode === 'incremental' ? '增量同步' : '全量同步'}`);
  
  const pool = mysql.createPool(CONFIG);
  
  try {
    // 测试连接
    const conn = await pool.getConnection();
    console.log('✅ 数据库连接成功\n');
    conn.release();
    
    const typesToSync = targetType 
      ? { [targetType]: SYNC_TYPES[targetType] }
      : SYNC_TYPES;
    
    const results = {};
    
    for (const [type, config] of Object.entries(typesToSync)) {
      if (!config) {
        console.log(`❌ 未知类型: ${type}`);
        continue;
      }
      
      console.log(`\n📄 同步 ${type}...`);
      results[type] = await syncType(pool, type, config, mode);
    }
    
    // 汇总报告
    console.log('\n' + '='.repeat(50));
    console.log('📊 同步结果汇总\n');
    
    let totalSynced = 0;
    let totalSkipped = 0;
    let totalErrors = 0;
    
    for (const [type, result] of Object.entries(results)) {
      if (!result) continue;
      console.log(`${type}: ✅ ${result.synced} | ⏭️ ${result.skipped} | ❌ ${result.errors.length}`);
      totalSynced += result.synced;
      totalSkipped += result.skipped;
      totalErrors += result.errors.length;
    }
    
    console.log(`\n总计: ✅ ${totalSynced} | ⏭️ ${totalSkipped} | ❌ ${totalErrors}`);
    
    await pool.end();
    process.exit(totalErrors > 0 ? 1 : 0);
    
  } catch (error) {
    console.error('❌ 同步失败:', error.message);
    await pool.end();
    process.exit(1);
  }
}

async function syncType(pool, type, config, mode) {
  const { table, idField, file, fallbackFile, validate, insert } = config;
  
  // 读取文件
  let filePath = file;
  if (!fs.existsSync(file)) {
    if (fs.existsSync(fallbackFile)) {
      console.log(`  ⚠️ 主文件不存在，使用示例文件: ${fallbackFile}`);
      filePath = fallbackFile;
    } else {
      console.log(`  ⚠️ 文件不存在: ${file}`);
      console.log(`  📝 请创建 ${file} 或确保示例文件存在`);
      return { synced: 0, skipped: 0, errors: ['File not found'] };
    }
  }
  
  const data = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
  
  if (!Array.isArray(data)) {
    return { synced: 0, skipped: 0, errors: ['Data must be an array'] };
  }
  
  console.log(`  📊 待同步记录: ${data.length}`);
  
  let synced = 0;
  let skipped = 0;
  const errors = [];
  
  const conn = await pool.getConnection();
  
  try {
    for (const item of data) {
      try {
        // 验证数据
        const validation = validate(item);
        if (!validation.valid) {
          errors.push(`[${item[idField]}] 验证失败: ${validation.reason}`);
          continue;
        }
        
        // 检查是否已存在
        const [rows] = await conn.execute(
          `SELECT COUNT(*) as count FROM ${table} WHERE ${idField} = ?`,
          [item[idField]]
        );
        
        if (mode === 'incremental' && rows[0].count > 0) {
          skipped++;
          continue;
        }
        
        // 插入/更新
        await insert(conn, table, item);
        synced++;
        
      } catch (err) {
        errors.push(`[${item[idField]}] ${err.message}`);
      }
    }
    
  } finally {
    conn.release();
  }
  
  console.log(`  ✅ 成功: ${synced} | ⏭️ 跳过: ${skipped} | ❌ 错误: ${errors.length}`);
  
  if (errors.length > 0 && errors.length <= 5) {
    for (const err of errors) {
      console.log(`     - ${err}`);
    }
  }
  
  return { synced, skipped, errors };
}

// ============ 验证函数 ============

function validateKeywordCard(card) {
  if (!card.id || !card.name || !card.category) {
    return { valid: false, reason: '缺少必填字段(id/name/category)' };
  }
  if (!['器物', '职人', '风物', '情绪', '称谓'].includes(card.category)) {
    return { valid: false, reason: `无效的category: ${card.category}` };
  }
  if (!['凡', '珍', '奇', '绝'].includes(card.rarity)) {
    return { valid: false, reason: `无效的rarity: ${card.rarity}` };
  }
  return { valid: true };
}

function validateEventCard(card) {
  if (!card.id || !card.name || !card.dynasty) {
    return { valid: false, reason: '缺少必填字段(id/name/dynasty)' };
  }
  return { valid: true };
}

function validateAchievement(ach) {
  if (!ach.id || !ach.name || !ach.condition) {
    return { valid: false, reason: '缺少必填字段(id/name/condition)' };
  }
  return { valid: true };
}

// ============ 插入函数 ============

async function insertKeywordCard(conn, table, card) {
  const sql = `
    INSERT INTO ${table} (id, name, category, rarity, description, weight)
    VALUES (?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      category = VALUES(category),
      rarity = VALUES(rarity),
      description = VALUES(description),
      weight = VALUES(weight)
  `;
  await conn.execute(sql, [
    card.id,
    card.name,
    card.category,
    card.rarity || '凡',
    card.description || '',
    card.weight || 10,
  ]);
}

async function insertEventCard(conn, table, card) {
  const sql = `
    INSERT INTO ${table} (id, name, dynasty, location, description, characters)
    VALUES (?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      dynasty = VALUES(dynasty),
      location = VALUES(location),
      description = VALUES(description),
      characters = VALUES(characters)
  `;
  await conn.execute(sql, [
    card.id,
    card.name,
    card.dynasty,
    card.location || '',
    card.description || '',
    JSON.stringify(card.characters || []),
  ]);
}

async function insertAchievement(conn, table, ach) {
  const sql = `
    INSERT INTO ${table} (id, name, description, condition, reward)
    VALUES (?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      description = VALUES(description),
      condition = VALUES(condition),
      reward = VALUES(reward)
  `;
  await conn.execute(sql, [
    ach.id,
    ach.name,
    ach.description || '',
    JSON.stringify(ach.condition),
    JSON.stringify(ach.reward || {}),
  ]);
}

// 运行
main();
