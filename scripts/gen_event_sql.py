#!/usr/bin/env python3
"""
Comprehensive SQL generator for event_card table (EV001-EV600).
Reads from:
  - 02-event_cards.sql (EV001-EV123, existing)
  - gen_sql_final.py   (EV124-EV268, tuples)
  - gen_extended_events.py (EV124-EV267, backup add() calls)
  - gen_events_ev269.py (EV269-EV369, add() calls from sub-agent)
  - gen_600_events.py later= (EV370-EV393, 12-field tuples) — only from later section
  - manual_data: EV394 + EV469-EV600
Outputs: docker/mysql/init/02-event_cards.sql
"""
import re, sys, os

sys.stdout.reconfigure(encoding='utf-8')

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_PATH   = os.path.join(SCRIPT_DIR, "..", "docker", "mysql", "init", "02-event_cards.sql")
OUT_PATH   = os.path.normpath(OUT_PATH)

# ─── helpers ─────────────────────────────────────────────────────────────────

def esc(s):
    if s is None: return ""
    return str(s).replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ")

def card_num(cn):
    m = re.search(r'(\d+)', cn)
    return int(m.group(1)) if m else 0

# ─── 1. Load EV001-EV123 from existing SQL file ─────────────────────────────
existing_sql = {}
sql_pat = re.compile(
    r"\('(EV\d+)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*(\d+),\s*'(.*?)'\)",
    re.DOTALL,
)
existing_path = os.path.join(SCRIPT_DIR, "..", "docker", "mysql", "init", "02-event_cards.sql")
if os.path.exists(existing_path):
    with open(existing_path, encoding="utf-8") as f:
        content = f.read()
    for m in sql_pat.finditer(content):
        cn = m.group(1)
        existing_sql[cn] = {
            "card_no": cn, "title": m.group(2), "dynasty": m.group(3),
            "location": m.group(4), "description": m.group(5),
            "weight": int(m.group(6)), "era": m.group(7),
        }

# ─── 2. Load EV124-EV268 from gen_sql_final.py (tuples) ────────────────────
final_data = {}
tuple_pat = re.compile(
    r"\('(EV\d+)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*(\d+),\s*'(.*?)'\)",
    re.DOTALL,
)
final_path = os.path.join(SCRIPT_DIR, "gen_sql_final.py")
if os.path.exists(final_path):
    with open(final_path, encoding="utf-8") as f:
        content = f.read()
    for m in tuple_pat.finditer(content):
        cn = m.group(1)
        n = card_num(cn)
        if 124 <= n <= 268:
            final_data[cn] = {
                "card_no": cn, "title": m.group(2), "dynasty": m.group(3),
                "location": m.group(4), "description": m.group(5),
                "weight": int(m.group(6)), "era": m.group(7),
            }

# ─── 3. Load EV124-EV267 from gen_extended_events.py (add() calls) ─────────
ext_data = {}
ext_pat = re.compile(
    r"add\(\s*['\"](EV\d+)['\"],\s*['\"](.*?)['\"],\s*['\"](.*?)['\"],\s*['\"](.*?)['\"],\s*['\"](.*?)['\"],\s*(\d+),\s*['\"](.*?)['\"]\)",
    re.DOTALL,
)
ext_path = os.path.join(SCRIPT_DIR, "gen_extended_events.py")
if os.path.exists(ext_path):
    with open(ext_path, encoding="utf-8") as f:
        content = f.read()
    for m in ext_pat.finditer(content):
        cn = m.group(1)
        n = card_num(cn)
        if 124 <= n <= 267 and cn not in final_data:
            ext_data[cn] = {
                "card_no": cn, "title": m.group(2), "dynasty": m.group(3),
                "location": m.group(4), "description": m.group(5),
                "weight": int(m.group(6)), "era": m.group(7),
            }

# ─── 4. Load EV269-EV369 from gen_events_ev269.py (add() calls) ─────────────
ev269_data = {}
ev269_path = os.path.join(SCRIPT_DIR, "gen_events_ev269.py")
if os.path.exists(ev269_path):
    with open(ev269_path, encoding="utf-8") as f:
        content = f.read()
    for m in ext_pat.finditer(content):
        cn = m.group(1)
        n = card_num(cn)
        if 269 <= n <= 369:
            ev269_data[cn] = {
                "card_no": cn, "title": m.group(2), "dynasty": m.group(3),
                "location": m.group(4), "description": m.group(5),
                "weight": int(m.group(6)), "era": m.group(7),
            }

# ─── 5. Load EV370-EV393 from gen_600_events.py later= section only ─────────
later_data = {}
later_path = os.path.join(SCRIPT_DIR, "gen_600_events.py")
if os.path.exists(later_path):
    with open(later_path, encoding="utf-8") as f:
        content = f.read()
    # Only search within the later list section
    later_start = content.find('later = [')
    if later_start >= 0:
        later_section = content[later_start:]
        # 12-field tuple format: ("EVnnn","title","dyn",year,"loc",lat,lng,"desc",rarity,"high","mid","low")
        later_pat = re.compile(
            r'\"EV(\d+)\",\"([^\"]+)\",\"([^\"]+)\",(-?\d+),\"([^\"]*)\",(-?\d+\.?\d*),(-?\d+\.?\d*),\"([^\"]*)\",(\d+)'
        )
        for m in later_pat.finditer(later_section):
            n = int(m.group(1))
            if 370 <= n <= 393:
                rarity = int(m.group(9))
                weight = 100 if rarity >= 4 else (80 if rarity >= 3 else 50)
                later_data[f"EV{n}"] = {
                    "card_no": f"EV{n}", "title": m.group(2), "dynasty": m.group(3),
                    "location": m.group(5), "description": m.group(8),
                    "weight": weight, "era": m.group(3),
                }

# ─── 6. Manual data: EV394 (malformed in source) + EV395-EV468 + EV469-EV600 ─
manual_data = {
    # ===== EV394: 元末 (malformed in gen_600_events.py) =====
    "EV394": {"card_no":"EV394","title":"红巾军起义","dynasty":"元","location":"颍州（今安徽阜阳）","description":"韩山童、刘福通在颍州发动红巾军起义，以明王为号召，各地纷纷响应，元末农民战争全面爆发。","weight":90,"era":"元末"},

    # ===== EV395~EV468: 元末→明中期 (gap not in any file) =====
    "EV395": {"card_no":"EV395","title":"刘福通起兵颍州","dynasty":"元","location":"颍州（今安徽阜阳）","description":"刘福通在颍州发动红巾军起义，各地纷纷响应，元朝统治开始动摇。","weight":90,"era":"元末"},
    "EV396": {"card_no":"EV396","title":"芝麻李彭大起义","dynasty":"元","location":"徐州","description":"芝麻李等人在徐州发动红巾军起义，声势浩大，元朝调集重兵镇压。","weight":70,"era":"元末"},
    "EV397": {"card_no":"EV397","title":"郭子兴起兵濠州","dynasty":"元","location":"濠州（今安徽凤阳）","description":"郭子兴在濠州响应红巾军起义，后来成为朱元璋加入的队伍。","weight":80,"era":"元末"},
    "EV398": {"card_no":"EV398","title":"彭莹玉传教布道","dynasty":"元","location":"淮西地区","description":"白莲教僧人彭莹玉在淮西传播白莲教，组织发动红巾军起义。","weight":50,"era":"元末"},
    "EV399": {"card_no":"EV399","title":"徐寿辉建天完国","dynasty":"元","location":"蕲水","description":"徐寿辉在蕲水建立天完国，与红巾军呼应，成为元末农民起义的重要力量。","weight":80,"era":"元末"},
    "EV400": {"card_no":"EV400","title":"脱脱主政改革","dynasty":"元","location":"大都","description":"丞相脱脱推行改革，整顿吏治，但因得罪权贵被贬，改革半途而废。","weight":50,"era":"元末"},
    "EV401": {"card_no":"EV401","title":"高邮之战","dynasty":"元","location":"高邮（今江苏）","description":"张士诚在高邮大败元军，元朝围攻高邮不下，元军内部发生政变，脱脱被罢官。","weight":85,"era":"元末"},
    "EV402": {"card_no":"EV402","title":"元顺帝怠政","dynasty":"元","location":"大都","description":"元顺帝怠于政事，荒淫无度，朝政混乱，宫廷内部斗争不断。","weight":50,"era":"元末"},
    "EV403": {"card_no":"EV403","title":"朱元璋投奔郭子兴","dynasty":"元","location":"濠州","description":"朱元璋加入郭子兴的红巾军，因聪明能干被赏识，将养女马氏嫁给他。","weight":90,"era":"元末"},
    "EV404": {"card_no":"EV404","title":"朱元璋攻取集庆","dynasty":"元","location":"集庆（今南京）","description":"朱元璋率军攻取集庆，改名应天府，作为自己的根据地，开始独立发展。","weight":90,"era":"元末"},
    "EV405": {"card_no":"EV405","title":"小明王韩林儿","dynasty":"元","location":"亳州","description":"韩林儿称小明王，继承刘福通的红巾军势力，朱元璋一度名义上归附。","weight":70,"era":"元末"},
    "EV406": {"card_no":"EV406","title":"陈友谅杀徐寿辉","dynasty":"元","location":"江州","description":"陈友谅杀徐寿辉自立为帝，建立汉国，成为朱元璋最强的对手。","weight":85,"era":"元末"},
    "EV407": {"card_no":"EV407","title":"鄱阳湖之战","dynasty":"元","location":"鄱阳湖","description":"朱元璋与陈友谅在鄱阳湖展开决战，朱元璋以少胜多，陈友谅战死。","weight":100,"era":"元末"},
    "EV408": {"card_no":"EV408","title":"张士诚自称吴王","dynasty":"元","location":"平江（今苏州）","description":"张士诚在平江自称吴王，割据江浙富庶地区，与朱元璋对峙。","weight":80,"era":"元末"},
    "EV409": {"card_no":"EV409","title":"朱元璋平定陈友谅","dynasty":"元","location":"武昌","description":"朱元璋攻灭陈友谅的汉国，统一了长江中游，为建立明朝奠定基础。","weight":90,"era":"元末"},
    "EV410": {"card_no":"EV410","title":"朱元璋北伐灭元","dynasty":"明","location":"大都","description":"朱元璋命徐达、常遇春率军北伐，元顺帝北逃，元朝在中原的统治结束。","weight":100,"era":"明朝建立"},
    "EV411": {"card_no":"EV411","title":"朱元璋称帝建明","dynasty":"明","location":"南京","description":"朱元璋在南京称帝，建立明朝，年号洪武，开启汉族王朝统治。","weight":100,"era":"明朝初期"},
    "EV412": {"card_no":"EV412","title":"朱元璋大封功臣","dynasty":"明","location":"南京","description":"朱元璋大封功臣，李善长、徐达等被封为公爵，功臣集团形成。","weight":50,"era":"明朝初期"},
    "EV413": {"card_no":"EV413","title":"胡惟庸案","dynasty":"明","location":"南京","description":"朱元璋以胡惟庸谋反案为由，大兴牢狱，牵连死者数万人，废除丞相制度。","weight":90,"era":"明朝初期"},
    "EV414": {"card_no":"EV414","title":"空印案","dynasty":"明","location":"南京","description":"朱元璋严惩空白盖印文书之罪，地方官员多受牵连，株连无数。","weight":50,"era":"明朝初期"},
    "EV415": {"card_no":"EV415","title":"郭桓案","dynasty":"明","location":"南京","description":"户部侍郎郭桓贪污事发，朱元璋严加追查，牵连无数，全国中等门户多破产。","weight":50,"era":"明朝初期"},
    "EV416": {"card_no":"EV416","title":"洪武大诰","dynasty":"明","location":"南京","description":"朱元璋亲自编写《大诰》，以案例警示官员，整顿吏治。","weight":50,"era":"明朝初期"},
    "EV417": {"card_no":"EV417","title":"蓝玉案","dynasty":"明","location":"南京","description":"朱元璋以蓝玉谋反案为由，大肆诛杀功臣将领，株连一万五千余人。","weight":85,"era":"明朝初期"},
    "EV418": {"card_no":"EV418","title":"锦衣卫设立","dynasty":"明","location":"南京","description":"朱元璋设立锦衣卫，作为侍从皇帝的军事机构，兼管侦察逮捕。","weight":80,"era":"明朝初期"},
    "EV419": {"card_no":"EV419","title":"朱元璋分封诸王","dynasty":"明","location":"南京","description":"朱元璋将儿子分封到各地为藩王，以屏卫皇室，结果导致靖难之役。","weight":50,"era":"明朝初期"},
    "EV420": {"card_no":"EV420","title":"徐达北征沙漠","dynasty":"明","location":"北方边疆","description":"徐达率军北征沙漠，打击北元势力，明朝边界暂时稳定。","weight":80,"era":"明朝初期"},
    "EV421": {"card_no":"EV421","title":"刘伯温去世","dynasty":"明","location":"南京","description":"刘伯温辞官归里，被胡惟庸党羽毒杀，一代谋士陨落。","weight":70,"era":"明朝初期"},
    "EV422": {"card_no":"EV422","title":"朱元璋病逝","dynasty":"明","location":"南京","description":"朱元璋病逝，皇太孙朱允炆即位，是为建文帝，遗诏不允许藩王进京奔丧。","weight":80,"era":"明朝初期"},
    "EV423": {"card_no":"EV423","title":"建文帝削藩","dynasty":"明","location":"南京","description":"建文帝采纳齐泰、黄子澄建议，开始削藩，周王等藩王相继被废。","weight":85,"era":"靖难之役"},
    "EV424": {"card_no":"EV424","title":"燕王起兵靖难","dynasty":"明","location":"北平","description":"朱棣以清君侧为名，在北平起兵反抗，史称靖难之役。","weight":100,"era":"靖难之役"},
    "EV425": {"card_no":"EV425","title":"靖难三杰","dynasty":"明","location":"北平","description":"朱棣帐下姚广孝、解缙、胡广等谋士，成为靖难之役的核心人物。","weight":50,"era":"靖难之役"},
    "EV426": {"card_no":"EV426","title":"朱允炆失踪之谜","dynasty":"明","location":"南京","description":"朱棣攻入南京，建文帝在混乱中下落不明，成为千古之谜。","weight":100,"era":"靖难之役"},
    "EV427": {"card_no":"EV427","title":"永乐帝迁都北京","dynasty":"明","location":"北京","description":"朱棣迁都北京，以天子守国门的方式加强对北方边疆的控制。","weight":90,"era":"永乐盛世"},
    "EV428": {"card_no":"EV428","title":"郑和下西洋","dynasty":"明","location":"南京","description":"郑和率船队出使西洋，历时二十八年，到达三十余国，开创了大航海时代。","weight":100,"era":"永乐盛世"},
    "EV429": {"card_no":"EV429","title":"解缙编纂永乐大典","dynasty":"明","location":"南京","description":"解缙等编纂永乐大典，是中国历史上最大百科全书。","weight":80,"era":"永乐盛世"},
    "EV430": {"card_no":"EV430","title":"永乐帝亲征漠北","dynasty":"明","location":"漠北","description":"朱棣亲率大军北征漠北，五出塞外，打击蒙古势力。","weight":90,"era":"永乐盛世"},
    "EV431": {"card_no":"EV431","title":"土木堡之变","dynasty":"明","location":"土木堡","description":"明英宗御驾亲征瓦剌，在土木堡被俘，明朝由盛转衰。","weight":100,"era":"明代中后期"},
    "EV432": {"card_no":"EV432","title":"于谦守北京","dynasty":"明","location":"北京","description":"北京保卫战，于谦力挽狂澜，击退瓦剌大军，挽救了明朝。","weight":100,"era":"明代中后期"},
    "EV433": {"card_no":"EV433","title":"夺门之变","dynasty":"明","location":"北京","description":"明英宗复位，于谦被害，明朝政治陷入动荡。","weight":80,"era":"明代中后期"},
    "EV434": {"card_no":"EV434","title":"弘治中兴","dynasty":"明","location":"北京","description":"明孝宗励精图治，重用贤臣，明朝出现中兴局面。","weight":70,"era":"明代中后期"},
    "EV435": {"card_no":"EV435","title":"宁王朱宸濠之乱","dynasty":"明","location":"南昌","description":"宁王朱宸濠在南昌起兵谋反，被王阳明平定。","weight":80,"era":"明代中后期"},
    "EV436": {"card_no":"EV436","title":"王阳明平宁王之乱","dynasty":"明","location":"南昌","description":"王阳明以少胜多，迅速平定宁王之乱，展现了心学的实践力量。","weight":85,"era":"明代中后期"},
    "EV437": {"card_no":"EV437","title":"大礼议之争","dynasty":"明","location":"北京","description":"群臣与嘉靖帝就生父称号发生激烈争论，最终嘉靖帝获胜。","weight":50,"era":"明代中后期"},
    "EV438": {"card_no":"EV438","title":"戚继光抗倭","dynasty":"明","location":"浙江","description":"戚继光在浙江招募矿工，训练戚家军，平定东南沿海倭患。","weight":100,"era":"明代中后期"},
    "EV439": {"card_no":"EV439","title":"俺答封贡","dynasty":"明","location":"大同","description":"明朝封俺答为顺义王，在大同开互市，结束了与蒙古的多年战争。","weight":80,"era":"明代中后期"},
    "EV440": {"card_no":"EV440","title":"隆庆开关","dynasty":"明","location":"月港","description":"明朝解除海禁，允许民间赴海外贸易，月港成为主要出海口。","weight":70,"era":"明代中后期"},
    "EV441": {"card_no":"EV441","title":"张居正改革","dynasty":"明","location":"北京","description":"张居正推行一条鞭法等改革，整顿吏治，明朝出现短暂中兴。","weight":100,"era":"明代中后期"},
    "EV442": {"card_no":"EV442","title":"万历三大征","dynasty":"明","location":"宁夏/朝鲜/播州","description":"万历皇帝派兵平定宁夏哱拜之乱、抗击日本侵朝、征讨播州杨应龙。","weight":85,"era":"明代中后期"},
    "EV443": {"card_no":"EV443","title":"哥伦布发现美洲","dynasty":"明","location":"美洲","description":"哥伦布航行到达美洲，开启了全球史的新纪元。","weight":100,"era":"世界史"},
    "EV444": {"card_no":"EV444","title":"达·伽马到达印度","dynasty":"明","location":"印度","description":"达·伽马绕过好望角到达印度，开辟了欧洲到亚洲的新航路。","weight":90,"era":"世界史"},
    "EV445": {"card_no":"EV445","title":"马丁·路德宗教改革","dynasty":"明","location":"德国","description":"马丁·路德在维登堡发表九十五条论纲，宗教改革运动席卷欧洲。","weight":100,"era":"世界史"},
    "EV446": {"card_no":"EV446","title":"哥白尼日心说","dynasty":"明","location":"波兰","description":"哥白尼发表《天体运行论》，提出日心说，颠覆了中世纪的宇宙观。","weight":90,"era":"世界史"},
    "EV447": {"card_no":"EV447","title":"丰臣秀吉侵朝","dynasty":"明","location":"朝鲜","description":"日本关白丰臣秀吉率军入侵朝鲜，明朝派兵援朝，中日朝三国大战。","weight":90,"era":"明代中后期"},
    "EV448": {"card_no":"EV448","title":"李时珍编本草纲目","dynasty":"明","location":"武汉","description":"李时珍编纂《本草纲目》，是中医药学的里程碑著作。","weight":80,"era":"明代中后期"},
    "EV449": {"card_no":"EV449","title":"徐光启译几何原本","dynasty":"明","location":"北京","description":"徐光启与利玛窦合作翻译《几何原本》，开启了西学东渐的潮流。","weight":70,"era":"明代中后期"},
    "EV450": {"card_no":"EV450","title":"徐霞客游记","dynasty":"明","location":"江阴","description":"徐霞客游历天下三十余年，写成《徐霞客游记》，是地理学瑰宝。","weight":70,"era":"明代中后期"},
    "EV451": {"card_no":"EV451","title":"明末三大儒","dynasty":"明","location":"江浙","description":"顾炎武、黄宗羲、王夫之批判君主专制，提出初步的民主思想。","weight":80,"era":"明末"},
    "EV452": {"card_no":"EV452","title":"东林党议","dynasty":"明","location":"无锡","description":"东林党人与阉党在朝堂上激烈争论，明朝政治日益黑暗。","weight":70,"era":"明末"},
    "EV453": {"card_no":"EV453","title":"魏忠贤专权","dynasty":"明","location":"北京","description":"宦官魏忠贤操纵朝政，迫害东林党人，自称九千岁。","weight":80,"era":"明末"},
    "EV454": {"card_no":"EV454","title":"崇祯帝即位","dynasty":"明","location":"北京","description":"崇祯帝即位后铲除魏忠贤，但明朝积重难返，气数已尽。","weight":70,"era":"明末"},
    "EV455": {"card_no":"EV455","title":"袁崇焕守宁远","dynasty":"明","location":"宁远","description":"袁崇焕在宁远大败努尔哈赤，努尔哈赤因伤去世。","weight":90,"era":"明末"},
    "EV456": {"card_no":"EV456","title":"皇太极建国大清","dynasty":"明","location":"沈阳","description":"皇太极改后金为大清，在沈阳称帝，开始入关的准备。","weight":90,"era":"明末清初"},
    "EV457": {"card_no":"EV457","title":"袁崇焕被诛","dynasty":"明","location":"北京","description":"崇祯帝中反间计，以谋反罪处死袁崇焕，自毁长城。","weight":100,"era":"明末"},
    "EV458": {"card_no":"EV458","title":"高迎祥起义","dynasty":"明","location":"陕西","description":"高迎祥在陕西发动农民起义，成为闯王高迎祥的起源。","weight":80,"era":"明末"},
    "EV459": {"card_no":"EV459","title":"李自成闯王","dynasty":"明","location":"西安","description":"李自成继任闯王，提出均田免粮的口号，深得民心。","weight":85,"era":"明末"},
    "EV460": {"card_no":"EV460","title":"张献忠建立大西","dynasty":"明","location":"成都","description":"张献忠在成都建立大西国，与李自成遥相呼应。","weight":80,"era":"明末"},
    "EV461": {"card_no":"EV461","title":"洪承畴降清","dynasty":"明","location":"锦州","description":"洪承畴兵败松锦，被皇太极招降，成为清朝入关的重要帮手。","weight":70,"era":"明末清初"},
    "EV462": {"card_no":"EV462","title":"吴三桂降清","dynasty":"明","location":"山海关","description":"李自成攻入北京，吴三桂引清兵入关，在山海关大败李自成。","weight":100,"era":"明末清初"},
    "EV463": {"card_no":"EV463","title":"崇祯帝煤山自缢","dynasty":"明","location":"北京","description":"李自成攻入北京，崇祯帝在煤山自缢，明朝覆亡。","weight":100,"era":"明末"},
    "EV464": {"card_no":"EV464","title":"扬州十日","dynasty":"清","location":"扬州","description":"清军攻破扬州，多铎下令屠杀十日，扬州百姓死伤无数。","weight":100,"era":"清初"},
    "EV465": {"card_no":"EV465","title":"嘉定三屠","dynasty":"清","location":"嘉定","description":"清军攻破嘉定，因反抗剃发令，三次屠杀嘉定城。","weight":100,"era":"清初"},
    "EV466": {"card_no":"EV466","title":"郑成功收复台湾","dynasty":"清","location":"台湾","description":"郑成功率军驱逐荷兰殖民者，收复台湾，建立郑氏政权。","weight":100,"era":"清初"},
    "EV467": {"card_no":"EV467","title":"康熙帝擒鳌拜","dynasty":"清","location":"北京","description":"康熙帝在宫中使用计谋擒拿权臣鳌拜，开始亲政。","weight":90,"era":"清初"},
    "EV468": {"card_no":"EV468","title":"三藩之乱","dynasty":"清","location":"云南","description":"吴三桂、耿精忠、尚可喜三藩起兵叛乱，康熙帝下令平叛。","weight":90,"era":"清初"},

    # ===== EV469~EV520: 清末/近代 (132 events) =====
    "EV469": {"card_no":"EV469","title":"鸦片战争·南京条约","dynasty":"近代","location":"广州","description":"英国以林则徐禁烟为由发动侵略战争，清朝战败，被迫签订《南京条约》，割让香港，开放五口通商。","weight":100,"era":"近代"},
    "EV470": {"card_no":"EV470","title":"林则徐虎门销烟","dynasty":"近代","location":"虎门","description":"林则徐在虎门销毁鸦片二百三十余万斤，中英关系激化。","weight":90,"era":"近代"},
    "EV471": {"card_no":"EV471","title":"三元里抗英","dynasty":"近代","location":"广州三元里","description":"三元里民众自发组织抗英，包围英军，揭开民众抗敌序幕。","weight":80,"era":"近代"},
    "EV472": {"card_no":"EV472","title":"英法联军火烧圆明园","dynasty":"近代","location":"北京圆明园","description":"英法联军攻入北京，火烧圆明园，万园之园化为灰烬。","weight":100,"era":"近代"},
    "EV473": {"card_no":"EV473","title":"慈禧太后垂帘听政","dynasty":"近代","location":"北京","description":"慈安太后和慈禧太后在幕后垂帘听政，实际掌控清朝政权近半个世纪。","weight":80,"era":"近代"},
    "EV474": {"card_no":"EV474","title":"洋务运动兴起","dynasty":"近代","location":"北京","description":"恭亲王奕訢等人倡导洋务运动，引进西方技术，创办军事工业。","weight":80,"era":"近代"},
    "EV475": {"card_no":"EV475","title":"甲午战争黄海海战","dynasty":"近代","location":"黄海","description":"北洋水师与日本联合舰队在黄海交战，邓世昌殉国，北洋水师受挫。","weight":100,"era":"近代"},
    "EV476": {"card_no":"EV476","title":"马关条约李鸿章","dynasty":"近代","location":"马关","description":"李鸿章赴日本马关谈判，签订《马关条约》，割让台湾，赔款二亿两。","weight":100,"era":"近代"},
    "EV477": {"card_no":"EV477","title":"戊戌变法百日维新","dynasty":"近代","location":"北京","description":"光绪帝颁布《明定国是诏》，维新变法103天，颁布上百道新政诏令。","weight":100,"era":"近代"},
    "EV478": {"card_no":"EV478","title":"义和团运动","dynasty":"近代","location":"北京","description":"义和团大批涌入北京，烧教堂，杀教民，围攻使馆区，局势失控。","weight":90,"era":"近代"},
    "EV479": {"card_no":"EV479","title":"八国联军侵华","dynasty":"近代","location":"天津","description":"英法德俄日美意奥八国组成联军，攻陷天津北京，慈禧西逃。","weight":100,"era":"近代"},
    "EV480": {"card_no":"EV480","title":"辛亥革命武昌起义","dynasty":"近代","location":"武汉","description":"革命党人在武昌发动起义，湖北军政府成立，各省纷纷响应。","weight":100,"era":"近代"},
    "EV481": {"card_no":"EV481","title":"中华民国成立","dynasty":"近代","location":"南京","description":"孙中山在南京就任中华民国临时大总统，中华民国正式成立。","weight":100,"era":"近代"},
    "EV482": {"card_no":"EV482","title":"清帝退位","dynasty":"近代","location":"北京","description":"隆裕太后代表宣统帝颁布退位诏书，清朝灭亡，中华民国统一。","weight":100,"era":"近代"},
    "EV483": {"card_no":"EV483","title":"袁世凯就任大总统","dynasty":"近代","location":"北京","description":"袁世凯在北京就任中华民国大总统，革命党人妥协，北洋政府开始。","weight":80,"era":"近代"},
    "EV484": {"card_no":"EV484","title":"宋教仁被刺","dynasty":"近代","location":"上海","description":"国民党代理理事长宋教仁在上海被刺杀，舆论哗然，国民党发动二次革命。","weight":90,"era":"近代"},
    "EV485": {"card_no":"EV485","title":"二次革命","dynasty":"近代","location":"江西","description":"国民党人发动二次革命，讨伐袁世凯，失败后孙中山流亡日本。","weight":80,"era":"近代"},
    "EV486": {"card_no":"EV486","title":"袁世凯称帝","dynasty":"近代","location":"北京","description":"袁世凯接受劝进，在北京称帝，改元洪帆，护国战争爆发。","weight":100,"era":"近代"},
    "EV487": {"card_no":"EV487","title":"护国战争蔡锷","dynasty":"近代","location":"昆明","description