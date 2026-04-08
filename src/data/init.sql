-- =====================================================
-- arbitrary-gate 数据库初始化脚本
-- 数据库: arbitrary_gate
-- 编码: utf8mb4
-- =====================================================

CREATE DATABASE IF NOT EXISTS `arbitrary_gate` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `arbitrary_gate`;

-- ---------------------------------------------------
-- 1. 关键词卡牌表 keyword_card
-- ---------------------------------------------------
DROP TABLE IF EXISTS `keyword_card`;
CREATE TABLE `keyword_card` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `card_no`     VARCHAR(10)  NOT NULL COMMENT '卡牌编号 KW001',
  `name`        VARCHAR(50)  NOT NULL COMMENT '卡牌名称',
  `category`    TINYINT UNSIGNED NOT NULL COMMENT '分类: 1=器物, 2=职人, 3=风物, 4=情绪, 5=称谓',
  `rarity`      TINYINT UNSIGNED NOT NULL COMMENT '稀有度: 1=凡, 2=珍, 3=奇, 4=绝',
  `weight`      INT UNSIGNED NOT NULL DEFAULT 60 COMMENT '抽卡权重',
  `description` TEXT COMMENT '描述（预留）',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_category` (`category`),
  KEY `idx_rarity` (`rarity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键词卡牌表';

-- ---------------------------------------------------
-- 2. 历史事件卡牌表 event_card
-- ---------------------------------------------------
DROP TABLE IF EXISTS `event_card`;
CREATE TABLE `event_card` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `card_no`     VARCHAR(10)  NOT NULL COMMENT '卡牌编号 EV001',
  `title`       VARCHAR(100) NOT NULL COMMENT '事件标题',
  `dynasty`     VARCHAR(20) NOT NULL COMMENT '所属朝代',
  `location`    VARCHAR(50) NOT NULL COMMENT '发生地点',
  `description` TEXT         NOT NULL COMMENT '事件描述',
  `weight`      INT UNSIGNED NOT NULL DEFAULT 100 COMMENT '抽卡权重',
  `era`         VARCHAR(20)  DEFAULT NULL COMMENT '时代标签（如：秦末汉初）',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_card_no` (`card_no`),
  KEY `idx_dynasty` (`dynasty`),
  KEY `idx_location` (`location`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史事件卡牌表';

-- ---------------------------------------------------
-- 3. 用户表 user
-- ---------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password`    VARCHAR(255) NOT NULL COMMENT '密码（bcrypt加密）',
  `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar_url`  VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
  `score`       INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '积分/分数',
  `level`       INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '等级',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`  DATETIME     DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------------------------------------------------
-- 4. 故事/剧本表 story
-- ---------------------------------------------------
DROP TABLE IF EXISTS `story`;
CREATE TABLE `story` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `title`       VARCHAR(100) NOT NULL COMMENT '故事标题',
  `description` TEXT         DEFAULT NULL COMMENT '故事简介',
  `dynasty`     VARCHAR(20)  DEFAULT NULL COMMENT '所属朝代',
  `difficulty`  TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '难度: 1=简单, 2=普通, 3=困难, 4=噩梦',
  `chapter_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '章节数量',
  `cover_url`   VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=正常',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dynasty` (`dynasty`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故事/剧本表';

-- ---------------------------------------------------
-- 5. 故事章节表 story_chapter
-- ---------------------------------------------------
DROP TABLE IF EXISTS `story_chapter`;
CREATE TABLE `story_chapter` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `story_id`    INT UNSIGNED NOT NULL COMMENT '所属故事ID',
  `chapter_no`  INT UNSIGNED NOT NULL COMMENT '章节编号',
  `title`       VARCHAR(100) NOT NULL COMMENT '章节标题',
  `content`     TEXT         DEFAULT NULL COMMENT '章节内容/剧情',
  `keywords`    JSON         DEFAULT NULL COMMENT '本章可用关键词卡（如: ["KW001","KW002"]）',
  `events`      JSON         DEFAULT NULL COMMENT '本章可用历史事件卡（如: ["EV001"]）',
  `choice_a`    VARCHAR(200) DEFAULT NULL COMMENT '选项A描述',
  `choice_b`    VARCHAR(200) DEFAULT NULL COMMENT '选项B描述',
  `choice_result` TEXT       DEFAULT NULL COMMENT '选择结果/后续剧情',
  `next_chapter_id` INT UNSIGNED DEFAULT NULL COMMENT '下一章ID',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_story_id` (`story_id`),
  KEY `idx_chapter_no` (`chapter_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故事章节表';

-- ---------------------------------------------------
-- 6. 用户抽卡记录表 draw_record（扩展用）
-- ---------------------------------------------------
DROP TABLE IF EXISTS `draw_record`;
CREATE TABLE `draw_record` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id`     INT UNSIGNED NOT NULL COMMENT '用户ID',
  `card_type`   VARCHAR(10)  NOT NULL COMMENT '卡牌类型: keyword / event',
  `card_id`     INT UNSIGNED NOT NULL COMMENT '卡牌ID',
  `card_no`     VARCHAR(10)  NOT NULL COMMENT '卡牌编号',
  `draw_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_draw_time` (`draw_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽卡记录表';

-- ---------------------------------------------------
-- 7. 用户卡牌持有表 user_card（扩展用）
-- ---------------------------------------------------
DROP TABLE IF EXISTS `user_card`;
CREATE TABLE `user_card` (
  `id`          INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id`     INT UNSIGNED NOT NULL COMMENT '用户ID',
  `card_type`   VARCHAR(10)  NOT NULL COMMENT '卡牌类型: keyword / event',
  `card_id`     INT UNSIGNED NOT NULL COMMENT '卡牌ID',
  `card_no`     VARCHAR(10)  NOT NULL COMMENT '卡牌编号',
  `count`       INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '持有数量',
  `acquired_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_card` (`user_id`, `card_type`, `card_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户卡牌持有表';

-- =====================================================
-- 初始数据：关键词卡牌（100条）
-- =====================================================
INSERT INTO `keyword_card` (`id`, `card_no`, `name`, `category`, `rarity`, `weight`, `description`) VALUES
(1,'KW001','铜锁芯',1,1,60,'锈迹斑斑的铜锁内芯'),
(2,'KW002','旧船票',1,3,12,'一张看不清终点的旧船票'),
(3,'KW003','破风筝',1,1,60,'断了线的破旧风筝'),
(4,'KW004','断发梳',1,2,25,'缠着几缕断发的木梳'),
(5,'KW005','半块玉佩',1,3,12,'意外碎成两半的玉佩之一'),
(6,'KW006','搪瓷缸',1,1,60,'印着旧字号的搪瓷缸'),
(7,'KW007','锈铁剑',1,2,25,'剑身满是锈迹的旧铁剑'),
(8,'KW008','残竹简',1,3,12,'字迹模糊的残破竹简'),
(9,'KW009','破酒坛',1,1,60,'酒香犹存的破旧酒坛'),
(10,'KW010','旧棋盘',1,2,25,'一盘下了一半的旧棋'),
(11,'KW011','铜烛台',1,1,60,'造型古朴的铜烛台'),
(12,'KW012','褪色旗',1,2,25,'颜色褪尽的一面旧旗'),
(13,'KW013','碎瓷碗',1,1,60,'碎成几片的旧瓷碗'),
(14,'KW014','旧铜钱',1,1,60,'磨损严重的古铜钱'),
(15,'KW015','锈马镫',1,1,60,'锈迹斑斑的铁马镫'),
(16,'KW016','断弦琴',1,3,12,'断了一根弦的古琴'),
(17,'KW017','破罗盘',1,2,25,'指针失灵的旧罗盘'),
(18,'KW018','旧油灯',1,1,60,'灯油干涸的旧油灯'),
(19,'KW019','残瓦当',1,2,25,'宫殿遗落的残破瓦当'),
(20,'KW020','碎玉镯',1,3,12,'碎成数段的玉镯'),
(21,'KW021','说书匠',2,2,25,'以说书为生的老艺人'),
(22,'KW022','锔瓷匠',2,3,12,'修补破瓷的民间工匠'),
(23,'KW023','摆渡人',2,2,25,'渡人过河的沉默船夫'),
(24,'KW024','赶车人',2,1,60,'驾驭马车的普通车夫'),
(25,'KW025','卖酒妇',2,1,60,'街边卖酒的妇人'),
(26,'KW026','算命先生',2,2,25,'自称能知过去的算命人'),
(27,'KW027','秦腔伶人',2,3,12,'唱秦腔的戏曲艺人'),
(28,'KW028','刽子手',2,2,25,'行刑的冷酷刀手'),
(29,'KW029','铸剑师',2,3,12,'以铸剑为业的匠人'),
(30,'KW030','驿卒',2,1,60,'官道驿站传递公文的卒子'),
(31,'KW031','绣娘',2,1,60,'以刺绣为生的女子'),
(32,'KW032','猎户',2,1,60,'深山中的猎人为生者'),
(33,'KW033','渔夫',2,1,60,'以打渔为业的渔民'),
(34,'KW034','行商',2,1,60,'走南闯北的行脚商人'),
(35,'KW035','方士',2,3,12,'炼丹求仙的术士'),
(36,'KW036','武师',2,2,25,'以授武为业的武者'),
(37,'KW037','书生',2,1,60,'寒窗苦读的读书人'),
(38,'KW038','宫女',2,2,25,'深宫中伺候的宫女'),
(39,'KW039','老兵',2,2,25,'历经百战的老年士兵'),
(40,'KW040','草药贩',2,1,60,'走街串巷卖草药的人'),
(41,'KW041','青石板',3,1,60,'雨后泛光的青石板路'),
(42,'KW042','风雨桥',3,3,12,'黔东南山区特有的风雨桥'),
(43,'KW043','渡口船',3,1,60,'渡口边等待的旧木船'),
(44,'KW044','咸阳宫阙',3,4,3,'秦帝国心脏的宏伟宫殿'),
(45,'KW045','未央残瓦',3,3,12,'未央宫遗落的残破瓦片'),
(46,'KW046','骊山晚照',3,3,12,'夕阳下的骊山剪影'),
(47,'KW047','灞桥垂柳',3,2,25,'灞桥边的依依垂柳'),
(48,'KW048','函谷关',3,3,12,'西出函谷的千古要塞'),
(49,'KW049','乌江渡',3,3,12,'项羽自刎的乌江渡口'),
(50,'KW050','鸿门宴',3,4,3,'改变历史走向的那场宴会'),
(51,'KW051','垓下坡',3,4,3,'楚霸王最后的战场'),
(52,'KW052','赤壁矶',3,4,3,'火烧曹营的千古战场'),
(53,'KW053','五丈原',3,3,12,'诸葛亮最后的驻军之地'),
(54,'KW054','长安月',3,2,25,'长安城上的一轮明月'),
(55,'KW055','洛阳城',3,3,12,'十三朝古都洛阳城'),
(56,'KW056','雁门关',3,3,12,'北方抵御匈奴的险关'),
(57,'KW057','玉门关',3,3,12,'春风不度的玉门关'),
(58,'KW058','朱仙镇',3,2,25,'岳飞大捷的朱仙镇'),
(59,'KW059','临安街',3,2,25,'南宋都城繁华街市'),
(60,'KW060','崖山海',3,4,3,'南宋最后的战场'),
(61,'KW061','意难平',4,3,12,'心中挥之不去的遗憾'),
(62,'KW062','乍见欢',4,3,12,'初次相见的喜悦'),
(63,'KW063','舍不得',4,3,12,'难以割舍的情感'),
(64,'KW064','离别苦',4,2,25,'与亲人生离死别的苦楚'),
(65,'KW065','归乡切',4,2,25,'久别归乡的迫切心情'),
(66,'KW066','故国情',4,3,12,'对故国的深切思念'),
(67,'KW067','权谋炽',4,3,12,'朝堂上激烈的权谋斗争'),
(68,'KW068','英雄泪',4,3,12,'英雄末路的悲怆泪水'),
(69,'KW069','孤臣心',4,3,12,'忠臣孤立无援的赤心'),
(70,'KW070','痴人怨',4,2,25,'痴迷之人的无尽怨恨'),
(71,'KW071','亡国恨',4,4,3,'亡国之君的深仇大恨'),
(72,'KW072','复仇火',4,3,12,'燃烧心底的复仇之火'),
(73,'KW073','苍生苦',4,2,25,'天下百姓的苦难'),
(74,'KW074','太平愿',4,2,25,'对太平盛世的渴望'),
(75,'KW075','美人殇',4,3,12,'美人香消玉殒的悲剧'),
(76,'KW076','侠客行',4,2,25,'侠客仗剑天涯的豪情'),
(77,'KW077','帝王业',4,3,12,'建立帝业的雄心壮志'),
(78,'KW078','寒士吟',4,1,60,'寒门士子的不平之鸣'),
(79,'KW079','长恨歌',4,4,3,'唐玄宗与杨贵妃的千古恨事'),
(80,'KW080','大风歌',4,3,12,'刘邦《大风歌》的豪迈'),
(81,'KW081','娘亲舅',5,1,60,'母亲的兄弟，最亲的娘舅'),
(82,'KW082','掌上珍',5,2,25,'被捧在手心的珍宝'),
(83,'KW083','眼中钉',5,2,25,'恨不得除之而后快的仇敌'),
(84,'KW084','心头肉',5,2,25,'最心疼最在乎的人'),
(85,'KW085','阶下囚',5,3,12,'失去自由的囚徒'),
(86,'KW086','座上宾',5,2,25,'受尊重的座上客人'),
(87,'KW087','乱臣贼子',5,3,12,'祸乱朝纲的奸臣'),
(88,'KW088','开国功臣',5,4,3,'辅佐帝王建立新朝的功臣'),
(89,'KW089','亡国之君',5,4,3,'王朝覆灭的末代皇帝'),
(90,'KW090','白衣卿相',5,3,12,'未仕或落魄却有大才之人'),
(91,'KW091','天涯沦落人',5,2,25,'流落天涯的失意之人'),
(92,'KW092','幕后黑手',5,3,12,'在暗处操纵局势的人'),
(93,'KW093','替罪羔羊',5,3,12,'代人受过的无辜者'),
(94,'KW094','墙头草',5,1,60,'随风倒的投机者'),
(95,'KW095','过河卒',5,1,60,'只能前进的过河小卒'),
(96,'KW096','孤家寡人',5,3,12,'失去所有亲信的孤独帝王'),
(97,'KW097','开路先锋',5,2,25,'冲锋在前的先驱者'),
(98,'KW098','末代王朝',5,4,3,'即将覆灭的腐朽王朝'),
(99,'KW099','遗民泪',5,3,12,'亡国遗民的悲怆泪水'),
(100,'KW100','新朝气象',5,2,25,'新王朝建立后的崭新气象');

-- =====================================================
-- 初始数据：历史事件卡牌（30条）
-- =====================================================
INSERT INTO `event_card` (`id`, `card_no`, `title`, `dynasty`, `location`, `description`, `weight`, `era`) VALUES
(1,'EV001','巨鹿·破釜沉舟','秦','巨鹿','项羽率楚军渡河，凿沉船只，粉碎秦军主力',100,'秦末'),
(2,'EV002','咸阳·子婴降刘邦','秦','咸阳','秦王子婴素车白马，献传国玉玺，秦朝覆灭',100,'秦末'),
(3,'EV003','骊山·秦俑悲歌','秦','骊山','万千陶俑沉默伫立，诉说帝国余威与万民血泪',80,'秦'),
(4,'EV004','鸿门·项庄舞剑','楚汉','鸿门','范增举玦，项庄舞剑，沛公命悬一线',100,'楚汉'),
(5,'EV005','垓下·四面楚歌','楚汉','垓下','楚歌声起，霸王别姬，乌江自刎，楚汉争雄落幕',100,'楚汉'),
(6,'EV006','乌江·霸王别姬','楚汉','乌江','虞兮虞兮奈若何，霸王末路，英雄悲歌',90,'楚汉'),
(7,'EV007','长安·文景之治','汉','长安','轻徭薄赋，与民休息，汉室根基奠定',90,'西汉'),
(8,'EV008','漠北·封狼居胥','汉','漠北','霍去病追击匈奴至狼居胥山，封禅祭天',90,'西汉'),
(9,'EV009','马邑·和亲之约','汉','马邑','汉室以宗室女嫁匈奴单于，换取边境安宁',80,'西汉'),
(10,'EV010','赤壁·东风骤起','三国','赤壁','周瑜黄盖火攻曹营，东风相助，三国鼎立',100,'三国'),
(11,'EV011','洛阳·司马代魏','三国','洛阳','司马炎逼迫魏元帝禅让，晋朝建立',90,'三国'),
(12,'EV012','街亭·挥泪斩马谡','三国','街亭','马谡违亮部署，失守街亭，诸葛亮挥泪斩将',80,'三国'),
(13,'EV013','五丈原·星落秋风','三国','五丈原','诸葛亮星落五丈原，出师未捷身先死',90,'三国'),
(14,'EV014','淝水·风声鹤唳','南北朝','淝水','苻坚百万南征，东晋八万胜之，前秦土崩',90,'南北朝'),
(15,'EV015','采石·王猛扪虱','南北朝','采石','王猛扪虱而谈天下，苻坚如拨云见日',70,'南北朝'),
(16,'EV016','洛阳·十八子反','隋唐','洛阳','隋末群雄并起，十八路反王围攻洛阳',80,'隋末'),
(17,'EV017','玄武门·秦王射兄','隋唐','玄武门','李世民伏兵玄武门，射杀建成元吉，夺嫡即位',100,'唐初'),
(18,'EV018','渭水·白马之盟','楚汉','渭水','刘邦与项羽约定鸿沟分治，中分天下',80,'楚汉'),
(19,'EV019','马嵬驿·贵妃缢死','隋唐','马嵬驿','安史之乱，贵妃被迫缢死于马嵬坡',100,'唐中'),
(20,'EV020','陈桥·黄袍加身','五代','陈桥','赵匡胤陈桥兵变，黄袍加身，宋朝建立',90,'五代'),
(21,'EV021','崖山·十万投海','五代','崖山','南宋末帝跳海，十万军民追随，崖山之后无中国',100,'南宋'),
(22,'EV022','风波·岳飞冤死','宋元','风波亭','岳飞以莫须有罪名被害，精忠报国成绝唱',100,'南宋'),
(23,'EV023','临安·崖山之后','宋元','临安','临安陷落，南宋流亡，文明坠落深谷',90,'南宋'),
(24,'EV024','山海关·吴三桂降清','明清','山海关','吴三桂冲冠一怒为红颜，引清兵入关',100,'明末'),
(25,'EV025','甲午·黄海烽火','明清','黄海','北洋水师全军覆没，中华千年变局开端',90,'清末'),
(26,'EV026','汜水·刘邦斩蛇','楚汉','汜水','刘邦酒后斩白蛇，传说为赤帝子斩白帝子',70,'楚汉'),
(27,'EV027','彭城·项羽三万破五十六万','楚汉','彭城','项羽以三万精骑击溃刘邦五十六万诸侯联军',85,'楚汉'),
(28,'EV028','长安·贞观之治','隋唐','长安','唐太宗虚心纳谏，开创大唐盛世',90,'唐初'),
(29,'EV029','幽州·安禄山起兵','隋唐','幽州','安禄山以讨杨国忠为名，起兵范阳，安史之乱始',85,'唐中'),
(30,'EV030','扬州·史可法殉国','明清','扬州','史可法困守扬州，城破殉国，清军屠城十日',85,'明末');
