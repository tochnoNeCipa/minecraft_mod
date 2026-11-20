#!/usr/bin/env python3
"""Build a clean, versioned defense kit from tracked files and verified release evidence.
Requires Python 3 and pandoc (DOCX + self-contained HTML). Does not rebuild the mod.
"""
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import html
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
BUILD = ROOT / 'build' / 'defense'
KIT = BUILD / ('RoboHelper-защита-' + VERSION)
ARCHIVE = BUILD / f'robohelper-{VERSION}-defense.zip'
JAR = ROOT / 'build/libs' / f'robohelper-{VERSION}.jar'
EVIDENCE = ROOT / 'docs/validation' / VERSION
GUIDES = ROOT / 'docs/defense'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()


def convert(inputs, target, title):
    command = ['pandoc', *map(str, inputs), '--from=markdown', '--standalone',
               '--metadata', 'title=' + title,
               '--resource-path=' + os.pathsep.join([str(EVIDENCE), str(ROOT / 'docs')]),
               '--output', str(target)]
    if target.suffix == '.html':
        command += ['--embed-resources']
    subprocess.run(command, cwd=ROOT, check=True)


def main():
    if not shutil.which('pandoc'):
        raise SystemExit('Install pandoc to generate the Word and HTML instructions.')
    if not JAR.exists():
        raise SystemExit(f'Build {JAR.name} first with ./gradlew build.')
    manifest = json.loads((EVIDENCE / 'manifest.json').read_text())
    if sha(JAR) != manifest['sha256']:
        raise SystemExit('JAR does not match the recorded acceptance evidence. Do not package unrelated test results.')
    with zipfile.ZipFile(JAR) as jar:
        assert json.loads(jar.read('fabric.mod.json'))['version'] == VERSION
    try:
        checkout = Path(git('rev-parse', '--show-toplevel')).resolve()
    except subprocess.CalledProcessError:
        raise SystemExit('Package from the original Git checkout; Gradle source builds do not require this packaging command.')
    if checkout != ROOT:
        raise SystemExit('Run packaging from the original project Git checkout, not from an exported source copy.')
    BUILD.mkdir(parents=True, exist_ok=True)
    if KIT.exists():
        assert KIT.parent == ROOT / 'build/defense'
        shutil.rmtree(KIT)
    mod = KIT / '01_МОД'
    source = KIT / '02_ИСХОДНИКИ' / 'проект'
    reports = KIT / '03_ОТЧЁТЫ'
    instructions = KIT / '04_ИНСТРУКЦИИ'
    for path in [mod, source, reports, instructions]:
        path.mkdir(parents=True, exist_ok=True)
    shutil.copy2(JAR, mod / JAR.name)
    (mod / 'УСТАНОВКА.txt').write_text(
        'Minecraft Java 1.20.1 + Fabric Loader 0.16.14+ + Fabric API 0.92.2+1.20.1.\n'
        'Положите этот JAR и Fabric API в mods нужного профиля. Замените старую версию RoboHelper.\n'
        'Fabric API: https://modrinth.com/mod/fabric-api\n'
        'Подробная инструкция: ../04_ИНСТРУКЦИИ/Инструкция_для_защиты.docx\n', encoding='utf-8-sig')
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    count = 0
    for name in filter(None, tracked):
        path = Path(name)
        assert not path.is_absolute() and '..' not in path.parts
        assert path.parts[0] not in {'.git', '.gradle', 'build', 'run', '.codex', '.aws'}
        destination = source / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / path, destination)
        count += 1
    assert count > 0 and (source / 'build.gradle').exists() and (source / 'src/client').is_dir()
    original = reports / 'исходные_данные'
    (original / 'docs').mkdir(parents=True)
    for name in ['VALIDATION.md', 'PROGRESS.md', 'ADDING_MODULES.md']:
        shutil.copy2(ROOT / 'docs' / name, original / 'docs' / name)
    shutil.copytree(EVIDENCE, original / 'docs/validation' / VERSION)
    (original / 'tools').mkdir()
    shutil.copy2(ROOT / 'tools/xorg-release.conf', original / 'tools/xorg-release.conf')
    guides = sorted(GUIDES.glob('[0-9][0-9]_*.md'))
    assert len(guides) == 3
    for path in guides:
        shutil.copy2(path, instructions / path.name)
    title = 'RoboHelper ' + VERSION + ': инструкция для защиты'
    convert(guides, instructions / 'Инструкция_для_защиты.docx', title)
    convert(guides, instructions / 'Инструкция_для_защиты.html', title)
    report_inputs = [ROOT / 'docs/VALIDATION.md', EVIDENCE / 'MEASUREMENTS.md']
    report_text = '\n\n'.join(path.read_text() for path in report_inputs)
    report_text = report_text.replace('(validation/', '(исходные_данные/docs/validation/').replace(
        '(../tools/', '(исходные_данные/tools/').replace(
        '(../../VALIDATION.md)', '(исходные_данные/docs/VALIDATION.md)')
    report_source = BUILD / 'report-source.md'
    report_source.write_text(report_text)
    convert([report_source], reports / 'Результаты_проверок.docx', 'RoboHelper: приёмка и измерения')
    convert([report_source], reports / 'ОТКРЫТЬ_ОТЧЁТ.html', 'RoboHelper: приёмка и измерения')
    (KIT / '00_НАЧНИ_ЗДЕСЬ.txt').write_text(
        'RoboHelper ' + VERSION + ' — комплект для защиты\n\n'
        '1. Распакуйте весь ZIP. Откройте 00_НАЧНИ_ЗДЕСЬ.html в браузере.\n'
        '2. Игровой JAR — 01_МОД.\n3. Исходники Gradle/Fabric — 02_ИСХОДНИКИ/проект.\n'
        '4. Word-отчёт, графики и исходные данные — 03_ОТЧЁТЫ.\n'
        '5. Запуск, карта кода и сценарий демонстрации — 04_ИНСТРУКЦИИ.\n\n'
        'Minecraft, Fabric Loader и Fabric API устанавливаются отдельно.\n'
        'Запуск игры и сборку исходников подготовьте до защиты.\n', encoding='utf-8-sig')
    items = [
        ('01_МОД', 'Игровой мод', 'JAR версии ' + VERSION + ' и краткая установка.'),
        ('02_ИСХОДНИКИ/проект', 'Исходный проект', 'Открыть в IntelliJ IDEA как Gradle-проект. Нужен JDK 17.'),
        ('03_ОТЧЁТЫ/Результаты_проверок.docx', 'Результаты проверок — Word', 'Матрица приёмки, таблицы, графики, скриншоты.'),
        ('03_ОТЧЁТЫ/ОТКРЫТЬ_ОТЧЁТ.html', 'Результаты проверок — браузер', 'Отчёт со встроенными изображениями; работает без интернета.'),
        ('04_ИНСТРУКЦИИ/Инструкция_для_защиты.docx', 'Инструкция для защиты — Word', 'Установка, команды, карта кода и порядок показа.'),
        ('04_ИНСТРУКЦИИ/Инструкция_для_защиты.html', 'Инструкция для защиты — браузер', 'Та же инструкция без необходимости устанавливать Word.'),
    ]
    cards = ''.join('<a class="card" href="' + html.escape(path, quote=True) + '"><strong>' + html.escape(label) + '</strong><span>' + html.escape(description) + '</span></a>' for path, label, description in items)
    page = '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>RoboHelper — комплект для защиты</title><style>
body{font:17px/1.6 system-ui,sans-serif;background:#f4f7fa;color:#1c2c3d;margin:0}main{max-width:920px;margin:50px auto;padding:24px}h1{line-height:1.15}p{max-width:780px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:16px}.card{display:block;background:white;border:1px solid #d8e1e9;border-radius:12px;padding:22px;color:inherit;text-decoration:none}.card:hover{border-color:#2878ac}.card strong{display:block;color:#17618f}.card span{display:block;margin-top:8px}small{display:block;margin-top:24px;color:#46576a}
</style><main><p>КУРСОВОЙ ПРОЕКТ · Minecraft Java 1.20.1 / Fabric</p><h1>RoboHelper — комплект для защиты</h1>
<p>Сначала распакуйте ZIP целиком. Здесь собраны мод, исходники, инструкции и результаты проверки. Начните с инструкции, затем подготовьте игру и откройте исходники в IDE.</p><div class="grid">''' + cards + '''</div><small>Отчёты относятся к проверкам 16.11.2026: 113 серверных тестов, два настоящих клиента, перезапуск и чанки. Условия и ограничения указаны в отчёте. Игровой мир, видео, презентация и оформленная пояснительная записка готовятся отдельно.</small></main></html>'''
    (KIT / '00_НАЧНИ_ЗДЕСЬ.html').write_text(page)
    provenance = {
        'version': VERSION, 'packagedAtUTC': datetime.now(timezone.utc).isoformat(),
        'sourceCommit': git('rev-parse', 'HEAD'), 'sourceWorkingTreeDirty': bool(git('status', '--porcelain')),
        'sourceFileCount': count, 'jar': JAR.name, 'jarSha256': sha(JAR),
        'recordedTestSourceCommit': manifest['testedSourceCommit'], 'recordedServerTests': manifest['serverTests'],
        'evidenceDate': '2026-11-16', 'note': 'Packaging does not rerun the recorded game tests.'}
    (KIT / 'КОМПЛЕКТ.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n')
    files = sorted(p for p in KIT.rglob('*') if p.is_file())
    (KIT / 'SHA256SUMS.txt').write_text(''.join(sha(p) + '  ' + p.relative_to(KIT).as_posix() + '\n' for p in files))
    with zipfile.ZipFile(ARCHIVE, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(KIT.rglob('*')):
            if path.is_file():
                archive.write(path, (Path(KIT.name) / path.relative_to(KIT)).as_posix())
    with zipfile.ZipFile(ARCHIVE) as archive:
        assert archive.testzip() is None
    print(KIT)
    print(ARCHIVE)
    print('SHA256:', sha(ARCHIVE))


if __name__ == '__main__':
    main()
