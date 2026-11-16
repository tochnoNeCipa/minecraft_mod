#!/usr/bin/env python3
"""Run two real clients, a full server restart, chunk persistence and profiling.
Only build/release-* directories are written. Requires an available X11 display.
First run ./gradlew writeReleaseClasspath -PreleaseChecks before this script.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--display', default=os.environ.get('DISPLAY', ':99'))
parser.add_argument('--fresh', action='store_true', help='Remove only the generated build/release-* test directories')
args = parser.parse_args()
java = Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin/java'
if not java.exists():
    raise SystemExit('Set JAVA_HOME to a Java 17 installation')
classpath = (BUILD / 'release-classpath.txt').read_text().strip()
launch = ROOT / '.gradle/loom-cache/launch.cfg'
results = BUILD / 'release-results'
server_dir = BUILD / 'release-server'
client_dirs = [BUILD / 'release-client-owner', BUILD / 'release-client-other']
if args.fresh:
    for path in [results, server_dir, *client_dirs]:
        assert path.parent == BUILD and path.name.startswith('release-')
        if path.exists():
            shutil.rmtree(path)
for path in [results, server_dir, *client_dirs]:
    path.mkdir(parents=True, exist_ok=True)
(server_dir / 'eula.txt').write_text('eula=true\n')
(server_dir / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25575
online-mode=false
enforce-secure-profile=false
level-name=world
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
max-players=4
view-distance=4
simulation-distance=4
sync-chunk-writes=true
allow-nether=false
difficulty=normal
max-tick-time=120000
''')

processes = []
handles = []
def start(role, directory, phase=None, username=None):
    env = os.environ.copy()
    env['DISPLAY'] = args.display
    env['LIBGL_ALWAYS_SOFTWARE'] = '1'
    command = [str(java), '-Xmx2G', '-Dfabric.development=true',
               '-Dfabric.dli.config=' + str(launch), '-Dfabric.dli.env=' + role,
               '-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.Knot' + role.title(),
               '-Drobohelper.release.output=' + str(results)]
    if phase:
        command += ['-Drobohelper.profile=true', '-Drobohelper.release.phase=' + phase]
    command += ['-cp', classpath, 'net.fabricmc.devlaunchinjector.Main']
    if role == 'server':
        command += ['nogui']
    else:
        command += ['--username', username, '--accessToken', '0', '--version', 'release-checks',
                    '--gameDir', str(directory), '--width', '1280', '--height', '720']
        (directory / 'options.txt').write_text('guiScale:2\nmaxFps:30\nrenderDistance:4\nsimulationDistance:4\nskipMultiplayerWarning:true\n')
    logfile = results / (phase + '.log' if phase else username + '.log')
    handle = logfile.open('w')
    handles.append(handle)
    process = subprocess.Popen(command, cwd=directory, env=env, stdout=handle, stderr=subprocess.STDOUT)
    processes.append(process)
    return process, logfile

def wait(process, logfile, timeout, ready=None):
    deadline = time.monotonic() + timeout
    previous = set()
    while time.monotonic() < deadline:
        text = logfile.read_text(errors='replace')
        for line in text.splitlines():
            if 'RELEASE ' in line and line not in previous:
                print(line, flush=True)
                previous.add(line)
        if ready and ready in text:
            return
        if process.poll() is not None:
            if ready:
                raise RuntimeError('Process exited before readiness: ' + str(logfile))
            if process.returncode:
                raise RuntimeError('Process failed: ' + str(logfile))
            return
        for directory in client_dirs:
            failure = directory / 'acceptance-failed.txt'
            if failure.exists():
                raise RuntimeError(failure.read_text())
        if (results / 'failure.json').exists():
            raise RuntimeError((results / 'failure.json').read_text())
        time.sleep(1)
    raise TimeoutError('Timed out: ' + str(logfile))

try:
    server, log = start('server', server_dir, phase='prepare')
    wait(server, log, 180, ready='RELEASE ready prepare')
    for directory, username in zip(client_dirs, ['ReleaseOwner', 'ReleaseOther']):
        start('client', directory, username=username)
    wait(server, log, 240)
    assert (results / 'checkpoint.json').exists(), 'No saved checkpoint'
    for process in processes[1:]:
        process.wait(timeout=45)
        assert process.returncode == 0, 'Client process failed'
    for directory in client_dirs:
        assert (directory / 'acceptance-passed.txt').exists(), 'Missing client success marker'
    server, log = start('server', server_dir, phase='verify')
    wait(server, log, 420)
    summary = json.loads((results / 'summary.json').read_text())
    assert summary['passed'], summary
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)
finally:
    for process in processes:
        if process.poll() is None:
            process.terminate()
    for process in processes:
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
    for handle in handles:
        handle.close()
