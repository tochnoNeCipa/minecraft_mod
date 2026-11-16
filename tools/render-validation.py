#!/usr/bin/env python3
"""Copy successful acceptance evidence and regenerate standalone charts/tables."""
from pathlib import Path
import json
import shutil
import statistics
from datetime import datetime
from zoneinfo import ZoneInfo
import xml.etree.ElementTree as ET
import os
ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault('MPLCONFIGDIR', str(ROOT / 'build/matplotlib-cache'))
os.environ.setdefault('XDG_CACHE_HOME', str(ROOT / 'build/render-cache'))
Path(os.environ['XDG_CACHE_HOME']).mkdir(parents=True, exist_ok=True)
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
BUILD = ROOT / 'build'
DEST = ROOT / 'docs/validation' / VERSION
release = BUILD / 'release-results'
summary = json.loads((release / 'summary.json').read_text())
assert summary['passed'] and summary['performanceThresholdsMet'], summary
report = ET.parse(BUILD / 'gametest-results.xml').getroot()
assert not report.findall('.//failure') and not report.findall('.//error') and not report.findall('.//skipped')
DEST.mkdir(parents=True, exist_ok=True)
for name in ['summary', 'network', 'restart', 'chunks', 'performance']:
    shutil.copy2(release / (name + '.json'), DEST / (name + '.json'))
for name in ['oak', 'tunnel', 'field']:
    shutil.copy2(BUILD / 'balance-results' / (name + '.json'), DEST / (name + '.json'))
shutil.copy2(BUILD / 'gametest-results.xml', DEST / 'gametest-results.xml')
for role in ['owner', 'other']:
    for image in ['robot-gui-ru', 'robot-gui-en', 'robots-world']:
        shutil.copy2(BUILD / ('release-client-' + role) / 'screenshots' / (image + '.png'), DEST / (role + '-' + image + '.png'))
performance = json.loads((DEST / 'performance.json').read_text())
rows = performance['results']
oak = json.loads((DEST / 'oak.json').read_text())
tunnel = json.loads((DEST / 'tunnel.json').read_text())
field = json.loads((DEST / 'field.json').read_text())
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10})
fig, axes = plt.subplots(1, 2, figsize=(10, 4), constrained_layout=True)
for scenario, label, color in [('empty-search-lumberjack+farmer+logistics', 'Поиск задач', '#3678a5'), ('mining+guard+logistics', 'Проходка тоннеля', '#bd6a37')]:
    data = [row for row in rows if row['scenario'] == scenario]
    for ax, key, title in zip(axes, ['meanTotalMs', 'p95TotalMs'], ['Среднее', '95-й процентиль']):
        ax.plot([r['robots'] for r in data], [r[key] for r in data], marker='o', label=label, color=color)
        ax.set(title=title, xlabel='Число роботов', ylabel='Суммарное время тика роботов, мс', ylim=(0, None))
        ax.set_xticks([1, 3, 5, 10]); ax.grid(alpha=.2)
axes[0].legend()
fig.suptitle('RoboHelper ' + VERSION + ': выделенный сервер, Java 17, 4 vCPU')
fig.savefig(DEST / 'performance.png', dpi=160)
plt.close(fig)
fig, axes = plt.subplots(1, 2, figsize=(9, 4), constrained_layout=True)
means = [statistics.mean(r['cutTicksFromJobStart'] / 20 for r in oak if r['level'] == level) for level in [1, 2, 3]]
stds = [statistics.stdev(r['cutTicksFromJobStart'] / 20 for r in oak if r['level'] == level) for level in [1, 2, 3]]
axes[0].bar([1, 2, 3], means, yerr=stds, capsize=4, color='#3678a5')
axes[0].set(title='Дуб: 6 брёвен, по 10 попыток', xlabel='Уровень', ylabel='Игровые секунды (среднее ± SD)')
axes[1].bar([r['level'] for r in tunnel], [r['ticks']/20 for r in tunnel], color='#bd6a37')
axes[1].set(title='Тоннель 15×1×2', xlabel='Уровень', ylabel='Игровые секунды')
for ax in axes:
    ax.set_xticks([1, 2, 3]); ax.grid(axis='y', alpha=.2); ax.set_axisbelow(True)
fig.savefig(DEST / 'balance.png', dpi=160)
plt.close(fig)
lines = [f'# Измерения RoboHelper {VERSION}', '',
         'Сервер: Linux, Java ' + performance['java'] + f", {performance['processors']} vCPU. Дата: {datetime.now(ZoneInfo('Asia/Qyzylorda')).strftime('%d.%m.%Y')}.", '',
         f"Серверные проверки: **{len(report.findall('.//testcase'))}/{len(report.findall('.//testcase'))}**. Сетевая приёмка, полный перезапуск и чанки: пройдены.", '',
         '## Нагрузка', '',
         'Перед серией — 800 тиков общего прогрева на 10 роботах (поиск и работа). Затем для каждого случая — 60 тиков прогрева и 240 измеряемых тиков. Клиенты к этому моменту завершены. Порядок в каждой группе: 1, 3, 5, 10. Измеряется весь серверный `RobotEntity.tick`, а не только код профессии. Дополнительно записан полный серверный тик.', '',
         '| Сценарий | Роботов | Среднее всего, мс | P95, мс | Максимум, мс | Полный серверный тик, среднее мс |',
         '| --- | ---: | ---: | ---: | ---: | ---: |']
for row in rows:
    name = 'Поиск (лесоруб/фермер/логист)' if row['scenario'].startswith('empty') else 'Шахтёр + охранник + логист'
    lines.append(f"| {name} | {row['robots']} | {row['meanTotalMs']:.3f} | {row['p95TotalMs']:.3f} | {row['maxTotalMs']:.3f} | {row['meanServerTickMs']:.3f} |")
lines += ['', 'Средние значения для одного робота ≤1 мс, суммарно для пяти ≤5 мс: требования НТ-04 выполнены в этих сценариях. P95 и максимумы не скрываются: порог ТЗ относится к среднему. JIT, GC и кэши влияют на сравнение, линейная зависимость нагрузки от числа роботов не утверждается. Это короткие контролируемые измерения, а не гарантия для любого ПК, большого мира или сборки модов.', '', '![Нагрузка](performance.png)', '',
          'Поиск: три установленных модуля, доступная зона без подходящих целей. Работа: реальные тоннели и добыча; охранник продолжает сканировать, логист установлен, но хранилище не достигает порога. Одновременный бой/выгрузка и активные фермы не входят в нагрузочную серию; их функциональная совместимость проверена GameTests.', '',
          '## Баланс', '', '| Уровень | Дуб: среднее до 6-го бревна, с | Полный цикл дуба: энергия | Тоннель 15 м: с | Энергия тоннеля (действия + ходьба) |', '| --- | ---: | ---: | ---: | ---: |']
for level in [1, 2, 3]:
    trial = [r for r in oak if r['level'] == level]
    t = next(r for r in tunnel if r['level'] == level)
    lines.append(f"| {level} | {statistics.mean(r['cutTicksFromJobStart'] for r in trial)/20:.3f} | {trial[0]['cycleEnergy']} | {t['ticks']/20:.2f} | {t['totalEnergy']} ({t['actionEnergy']} + {t['walkingEnergy']}) |")
lines += ['', 'Дуб — искусственно подготовленный ствол из шести брёвен с непостоянной листвой, рядом с роботом, по 10 попыток каждого уровня. Время от выдачи задания включает начало поиска; энергия полного цикла включает посадку. Тоннель 15×1×2 — по одной проходке каждого уровня. Времена переведены из игровых тиков при 20 TPS, независимо от скорости ускоренного GameTest.', '',
          f"Поле **9×9**: {field['crops']} спелых культур и одна клетка воды, {field['actions']} действий. Время — **{field['ticks']/20:.1f} с**, энергия — **{field['totalEnergy']}** ({field['actionEnergy']} действия + {field['walkingEnergy']} ходьба). Робот начал на уровне 1 и достиг уровня 2 в середине работы; скидка уже включена. Все культуры пересажены, farmland сохранён. Рост культур отключён на время замера.", '',
          '![Баланс](balance.png)', '',
          '## Интерфейс настоящих клиентов', '',
          'Скриншоты получены через framebuffer Minecraft в сетевом мире, без монтажа. Предметы и свойства синхронизированы с сервера.', '',
          '![Русский GUI](owner-robot-gui-ru.png)', '', '![English GUI](owner-robot-gui-en.png)', '',
          '## Ограничения', '',
          'OpenAL в облачной среде не открыл аудиоустройство, поэтому слышимость игровых звуков не подтверждена. Не выполнены длительное ручное прохождение, вход через Microsoft/Mojang и тесты со сторонними модами. Подробности и матрица Т-01…Т-28: [VALIDATION.md](../../VALIDATION.md).', '']
(DEST / 'MEASUREMENTS.md').write_text('\n'.join(lines))
print(DEST)
