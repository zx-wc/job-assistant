"""结构核对：md 标题数 vs docx 标题样式段落数，并检查目录与残留标记。

用法：python verify_docx.py [md目录] [docx目录]
"""
import glob
import os
import re
import sys

from docx import Document

MD = sys.argv[1] if len(sys.argv) > 1 else r'C:\soft\job-assistant\docs'
OUT = sys.argv[2] if len(sys.argv) > 2 else r'C:\Users\wangchuan\Desktop\软件工程实践实验\报告'


def count_md_headings(path):
    with open(path, 'r', encoding='utf-8') as handle:
        text = handle.read()
    # 只统计代码块之外的标题
    in_code = False
    count = 0
    for line in text.splitlines():
        if line.strip().startswith('```'):
            in_code = not in_code
            continue
        if not in_code and re.match(r'^#{1,6}\s+', line):
            count += 1
    return count


rows = []
for md_path in sorted(glob.glob(os.path.join(MD, '*.md'))):
    base = os.path.splitext(os.path.basename(md_path))[0]
    docx_path = os.path.join(OUT, base + '.docx')
    doc = Document(docx_path)
    heading_paras = [p for p in doc.paragraphs if p.style.name.startswith('Heading')]
    toc_label = any(p.text.strip() == '目  录' for p in doc.paragraphs)
    style_names = sorted({p.style.name for p in heading_paras})
    rows.append((base, count_md_headings(md_path), len(heading_paras), toc_label,
                 len(doc.tables), round(os.path.getsize(docx_path) / 1024, 1), style_names))
    print('%-34s md标题=%-3d docx标题=%-3d 目录=%-5s 表格=%-3d %6.1fKB %s'
          % rows[-1])

mismatch = [row for row in rows if row[1] - 1 != row[2]]
print('\n标题数不一致的文件：', [row[0] for row in mismatch] or '无')
print('缺少目录的文件：', [row[0] for row in rows if not row[3]] or '无')
