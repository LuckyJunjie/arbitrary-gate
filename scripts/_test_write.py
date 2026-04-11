
#!/usr/bin/env python3
import re, sys
sys.stdout.reconfigure(encoding='utf-8')

def escape(s):
    return s.replace('\\', '\\\\').replace("'", "\\'").replace('\n', ' ')

def sql_row(ev, title, dynasty, location, desc, weight, era):
    return (f"('{ev}','{escape(title)}','{escape(dynasty)}','{escape(location)}',"
            f"'{escape(desc)}',{weight},'{escape(era)}'")

all_events = []

# EV001-EV123: from existing SQL
ev123 = [
    ('EV001','涿鹿·黄帝崛起','先秦','涿鹿（今河北）','黄帝与炎帝在涿鹿之野展开华夏民族史上规模最大的部族战争，黄帝最终胜出，统一黄河流域各部落，开创华夏文明新纪元，奠定中华民族最初的文化认同。',80,'华夏始祖时代'),
    ('EV002','神农尝百草','先秦','荆楚地区','神农氏亲尝百草辨别药性，日遇七十二毒，以茶解之，开创中医药学之先河，被后世尊为农业之神与医药之祖。',50,'华夏始祖时代'),
    ('EV003','阪泉·炎黄之战','先秦','阪泉（今山西）','黄帝与炎帝两大部落在阪泉之野三度交战，黄帝最终收服炎帝部族，两族融合为华夏主体族群，中华民族由此而来。',80,'华夏始祖时代'),
]
print(f'Loaded {len(ev123)} events')
