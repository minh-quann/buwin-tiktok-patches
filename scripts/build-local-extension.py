#!/usr/bin/env python3
"""Build the TikTok Java/Kotlin extension with javac, Compose and R8, optionally update a local MPP.

Android/TikTok stubs are compile-only and are never included in the extension DEX.
Use the normal Gradle release pipeline for published releases.
"""
import argparse
import copy
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import importlib.util
from datetime import datetime, timezone
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]


def run(args):
    subprocess.run([str(arg) for arg in args], check=True)


def jar_classes(directory, output):
    with ZipFile(output, 'w', ZIP_DEFLATED) as jar:
        for source in sorted(directory.rglob('*.class')):
            jar.write(source, source.relative_to(directory).as_posix())


def compile_kotlin(android, destination):
    """Compile native Compose before javac with the privately bundled Kotlin/runtime libraries."""
    sources = sorted((ROOT / 'extensions/tiktok/src/main/kotlin').rglob('*.kt'))
    if not sources:
        return []
    artifacts = Path.home() / '.gradle/caches/modules-2/files-2.1'
    def cached(group, name, version=None):
        candidates = list((artifacts / group / name).glob((version or '*') + '/*/*.jar'))
        candidates = sorted(p for p in candidates if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
        if not candidates:
            raise RuntimeError('Need cached Kotlin build dependency: ' + group + ':' + name)
        return candidates[-1]
    # Use the reviewed compiler when available, or the same installed compiler used by the
    # local native-hook builder. No Gradle remote build cache or compiler plugins are loaded.
    reviewed = artifacts / 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.4.21'
    version = '2.4.21' if reviewed.is_dir() else '2.4.10'
    compiler = cached('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', version)
    stdlib = cached('org.jetbrains.kotlin', 'kotlin-stdlib', version)
    annotations = cached('org.jetbrains', 'annotations', '26.1.0')
    android_annotations = cached('androidx.annotation', 'annotation-jvm')
    runtime = [compiler, stdlib, annotations]
    for group, name in [('org.jetbrains.kotlin', 'kotlin-script-runtime'),
                        ('org.jetbrains.kotlin', 'kotlin-reflect'),
                        ('org.jetbrains.intellij.deps', 'trove4j'),
                        ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm')]:
        runtime.append(cached(group, name))
    spec = importlib.util.spec_from_file_location('glass_runtime', ROOT / 'scripts/glass-runtime.py')
    glass = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(glass)
    runtime_work = destination.parent / 'glass-runtime'
    if runtime_work.exists(): shutil.rmtree(runtime_work)
    runtime_work.mkdir()
    libraries = glass.inputs(runtime_work, android, run)
    sources += sorted((ROOT / 'extensions/tiktok/src/glass/kotlin').rglob('*.kt'))
    plugin = cached('org.jetbrains.kotlin', 'kotlin-compose-compiler-plugin-embeddable', version)
    run(['java', '-cp', os.pathsep.join(map(str, runtime)),
         'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
         '-jvm-target', '17', '-module-name', 'buwin_native_glass',
         '-Xjava-source-roots=' + ','.join(str(ROOT / path) for path in (
             'extensions/tiktok/src/main/java', 'extensions/shared/library/src/main/java',
             'extensions/tiktok/stub/src/main/java')),
         '-Xplugin=' + str(plugin),
         '-classpath', os.pathsep.join(map(str, [android, stdlib, annotations, android_annotations, *libraries])),
         '-d', destination, *sources])
    print('Compiled', len(sources), 'native Kotlin sources')
    return [stdlib, annotations, android_annotations, *libraries]


def update_cache(cache, dex):
    data = dex.read_bytes()
    with ZipFile(cache) as original:
        names = original.namelist()
        if names.count('extensions/tiktok.mpe') != 1 or len(names) != len(set(names)):
            raise ValueError('Expected exactly one extension and no duplicate ZIP entries')
        if any(n.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) for n in names):
            raise ValueError('Refusing to invalidate a signed bundle')
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        backup = cache.with_name(cache.name + '.backup-' + stamp)
        shutil.copy2(cache, backup)
        handle, temp_name = tempfile.mkstemp(prefix=cache.name + '.', dir=cache.parent)
        os.close(handle)
        temporary = Path(temp_name)
        try:
            with ZipFile(temporary, 'w') as result:
                result.comment = original.comment
                for entry in original.infolist():
                    result.writestr(copy.copy(entry), data if entry.filename == 'extensions/tiktok.mpe'
                                    else original.read(entry))
            with ZipFile(temporary) as result:
                if result.testzip() is not None:
                    raise ValueError('Updated archive failed its CRC check')
                assert result.read('extensions/tiktok.mpe') == data
                for entry in original.infolist():
                    if entry.filename != 'extensions/tiktok.mpe':
                        assert result.read(entry.filename) == original.read(entry)
            shutil.copymode(cache, temporary)
            os.replace(temporary, cache)
        finally:
            temporary.unlink(missing_ok=True)
    print('Cache verified:', cache)
    print('Backup:', backup)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get(
        'ANDROID_HOME', str(Path.home() / 'Android/Sdk'))))
    parser.add_argument('--android-platform', default='android-36')
    parser.add_argument('--build-tools', default='37.0.0')
    parser.add_argument('--output', type=Path, default=ROOT / 'extensions/tiktok/build/local-extension')
    parser.add_argument('--cache', type=Path, help='Local MPP to back up and update after compilation')
    args = parser.parse_args()
    android = args.sdk / 'platforms' / args.android_platform / 'android.jar'
    d8 = args.sdk / 'build-tools' / args.build_tools / 'd8'
    annotations = list((Path.home() / '.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-jvm').glob('*/*/*.jar'))
    annotations = sorted(p for p in annotations if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
    if not android.is_file() or not d8.is_file() or not annotations:
        raise SystemExit('Need Android SDK platform/build-tools and cached androidx annotation-jvm')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Always compile into fresh directories: deleted classes must not survive in the DEX.
    with tempfile.TemporaryDirectory(prefix='compile-', dir=output) as temporary:
        work = Path(temporary)
        stubs, classes = work / 'stubs', work / 'classes'
        stubs.mkdir()
        classes.mkdir()
        def compile_java(directories, destination, classpath):
            sources = sorted(p for directory in directories for p in (ROOT / directory).rglob('*.java'))
            source_list = work / (destination.name + '.txt')
            source_list.write_text('\n'.join('"' + str(p) + '"' for p in sources))
            run(['javac', '-encoding', 'UTF-8', '-source', '17', '-target', '17',
                 '-cp', os.pathsep.join(map(str, classpath)), '-d', destination, '@' + str(source_list)])
            print('Compiled', len(sources), 'Java sources into', destination.name)
        kotlin_dependencies = compile_kotlin(android, classes)
        compile_java(['extensions/tiktok/stub/src/main/java'], stubs, [android])
        compile_java(['extensions/shared/library/src/main/java', 'extensions/tiktok/src/main/java'],
                     classes, [android, stubs, classes, annotations[-1], *kotlin_dependencies])
        jar_classes(stubs, work / 'stubs.jar')
        jar_classes(classes, work / 'extension.jar')
        dex_directory = work / 'dex'
        dex_directory.mkdir()
        kotlin_classpath = [part for dependency in kotlin_dependencies for part in ['--classpath', dependency]]
        rules = work / 'glass-r8.pro'
        rules.write_text('''-keep class app.morphe.extension.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-repackageclasses app.morphe.extension.tiktok.glass.runtime
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**
-dontwarn android.view.DisplayListCanvas
-dontwarn android.view.RenderNode
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

''')
        program = [p for p in kotlin_dependencies if p.parent.name == 'glass-runtime']
        r8 = args.sdk / 'build-tools' / args.build_tools / 'lib/d8.jar'
        run(['java', '-Xmx5g', '-cp', r8, 'com.android.tools.r8.R8', '--release',
             '--min-api', '23', '--lib', android, '--lib', work / 'stubs.jar',
             '--pg-conf', rules, '--output', dex_directory, work / 'extension.jar', *program])
        dexes = list(dex_directory.glob('*.dex'))
        if len(dexes) != 1:
            raise ValueError('Expected a single extension DEX')
        dex = output / 'classes.dex'
        shutil.copy2(dexes[0], dex)
        assert dex.read_bytes().startswith(b'dex\n')
    print('DEX:', dex)
    print('SHA256:', hashlib.sha256(dex.read_bytes()).hexdigest())
    if args.cache:
        update_cache(args.cache.resolve(), dex)


if __name__ == '__main__':
    main()
