#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把课程 Markdown 文档批量转成排版好的 Word 文档（含封面、目录页码）。

流程（两遍渲染）：
    第 1 遍：生成不含页码的 docx → 用 Microsoft Word 导出 PDF → 读取 PDF 书签得到每个标题所在页
    第 2 遍：把页码写入目录 → 生成最终 docx → 再次导出 PDF 校验页码是否稳定（不稳定则再迭代一次）

用法：
    python build_course_docx.py <md目录> <输出目录>

依赖：python-docx、PyMuPDF、本机安装的 Microsoft Word（用于导出 PDF 取得真实页码）
"""

import glob
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import md2docx  # noqa: E402

try:
    import fitz  # PyMuPDF
except Exception:  # pragma: no cover
    fitz = None

WORD_PS = r'''
param([string]$Docx, [string]$Pdf)
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($Docx, $false, $true)
    $doc.ExportAsFixedFormat($Pdf, 17, $false, 0, 0, 0, 0, 0, $true, $true, 1, $true, $true, $false)
    $doc.Close(0)
} finally {
    $word.Quit()
    [System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
}
'''


def export_pdf(docx_path, pdf_path, ps_path):
    if os.path.exists(pdf_path):
        os.remove(pdf_path)
    subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps_path,
                    '-Docx', docx_path, '-Pdf', pdf_path], check=False, capture_output=True)
    return os.path.exists(pdf_path) and os.path.getsize(pdf_path) > 0


def bookmark_pages(pdf_path):
    document = fitz.open(pdf_path)
    toc = document.get_toc()
    document.close()
    return toc


def toc_entries_of(md_path):
    with open(md_path, 'r', encoding='utf-8') as handle:
        text = handle.read()
    blocks = md2docx.parse_blocks(text)
    return [(block[1], md2docx.plain(block[2])) for block in blocks
            if block[0] == 'heading' and 2 <= block[1] <= 3]


def pages_for(toc_entries, bookmarks):
    """把 PDF 书签中 2-3 级标题的页码，按顺序对齐到目录条目。"""
def normalize(text):
    """标题比较用：去掉空白、点号、顿号等，避免 Word 书签与原文标点差异导致匹配失败。"""
    return re.sub(r'[\s.．、,，:：;；\-—]+', '', text).lower()


def pages_for(toc_entries, bookmarks):
    """按顺序把 PDF 书签的页码对齐到目录条目（容忍标点/空格差异）。"""
    pages = []
    cursor = 0
    for _, text in toc_entries:
        target = normalize(text)
        found = None
        while cursor < len(bookmarks):
            _level, title, page = bookmarks[cursor]
            cursor += 1
            if normalize(title) == target:
                found = page
                break
        if found is None:
            return None
        pages.append(found)
    return pages

def main():
    if len(sys.argv) < 3:
        print('用法：python build_course_docx.py <md目录> <输出目录>')
        return 2
    md_dir, out_dir = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
    os.makedirs(out_dir, exist_ok=True)

    work = tempfile.mkdtemp(prefix='docx_build_')
    ps_path = os.path.join(work, 'word_export.ps1')
    with open(ps_path, 'w', encoding='utf-8-sig') as handle:
        handle.write(WORD_PS)

    md_files = sorted(glob.glob(os.path.join(md_dir, '*.md')))
    for md_path in md_files:
        base = os.path.splitext(os.path.basename(md_path))[0]
        final_docx = os.path.join(out_dir, base + '.docx')
        with open(md_path, 'r', encoding='utf-8') as handle:
            text = handle.read()

        entries = toc_entries_of(md_path)

        # 第 1 遍：不含页码
        first_docx = os.path.join(work, base + '_p1.docx')
        md2docx.build_document(text, base, None).save(first_docx)
        first_pdf = os.path.join(work, base + '_p1.pdf')
        pages = None
        if fitz is not None and export_pdf(first_docx, first_pdf, ps_path):
            pages = pages_for(entries, bookmark_pages(first_pdf))

        if not pages:
            print('[%s] 未能取得页码，输出不含页码的文档' % base)
            md2docx.build_document(text, base, None).save(final_docx)
            continue

        # 第 2 遍：写入页码，并校验页码是否稳定
        doc = md2docx.build_document(text, base, pages)
        doc.save(final_docx)
        second_pdf = os.path.join(work, base + '_p2.pdf')
        if export_pdf(final_docx, second_pdf, ps_path):
            pages2 = pages_for(entries, bookmark_pages(second_pdf))
            if pages2 and pages2 != pages:
                # 页码发生偏移：用新页码再生成一次
                md2docx.build_document(text, base, pages2).save(final_docx)
                print('[%s] 页码已修正并重新生成（%d 条目录）' % (base, len(entries)))
            else:
                print('[%s] 完成（%d 条目录，页码稳定）' % (base, len(entries)))
        else:
            print('[%s] 完成（%d 条目录，未做二次校验）' % (base, len(entries)))

    print('输出目录：', out_dir)
    return 0


if __name__ == '__main__':
    sys.exit(main())
