"""把指定 docx 的若干页渲染成 PNG，便于人工核验排版。

用法：python render_pages.py <docx路径> <页码,逗号分隔> [输出目录]
"""

import os
import subprocess
import sys

import fitz

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
}
'''


def main():
    if len(sys.argv) < 3:
        print('用法：python render_pages.py <docx路径> <页码,逗号分隔> [输出目录]')
        return 2
    docx_path = os.path.abspath(sys.argv[1])
    pages = [int(item) for item in sys.argv[2].split(',') if item.strip()]
    out_dir = os.path.abspath(sys.argv[3]) if len(sys.argv) > 3 else os.environ.get(
        'TEMP', r'C:\Windows\Temp')
    base = os.path.splitext(os.path.basename(docx_path))[0]

    ps_path = os.path.join(out_dir, '_render.ps1')
    os.makedirs(out_dir, exist_ok=True)
    with open(ps_path, 'w', encoding='utf-8-sig') as handle:
        handle.write(WORD_PS)

    pdf_path = os.path.join(out_dir, base + '.pdf')
    if os.path.exists(pdf_path):
        os.remove(pdf_path)
    subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps_path,
                    '-Docx', docx_path, '-Pdf', pdf_path], check=False, capture_output=True)
    if not os.path.exists(pdf_path):
        print('PDF 导出失败')
        return 1

    document = fitz.open(pdf_path)
    print('总页数：', document.page_count)
    for number in pages:
        if number < 1 or number > document.page_count:
            continue
        pix = document[number - 1].get_pixmap(dpi=110)
        png = os.path.join(out_dir, '%s_p%d.png' % (base, number))
        pix.save(png)
        print('png:', png)
    document.close()
    return 0


if __name__ == '__main__':
    sys.exit(main())
