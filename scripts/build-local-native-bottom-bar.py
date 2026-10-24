#!/usr/bin/env python3
"""Rebuild a native bottom-bar or subtitle patch and Java extension in a local Morphe cache.

Uses the installed Desktop patcher API as a classpath when the Gradle registry is unavailable.
Preserves all unrelated JVM classes/resources and regenerates the Android patch DEX as well.
Published releases should use the normal full Gradle pipeline.
"""
import argparse
import copy
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]


def run(args):
    subprocess.run(list(map(str, args)), check=True)


def artifact(group, name, version):
    directory = Path.home() / '.gradle/caches/modules-2/files-2.1' / group / name / version
    matches = [p for p in directory.glob('*/*.jar')
               if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if len(matches) != 1:
        raise RuntimeError(f'Need exactly one cached {name}:{version}, found {len(matches)}')
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--prepare-only', action='store_true', help='Build/verify without replacing the cache')
    parser.add_argument('--subtitle-tools', action='store_true',
                        help='Rebuild Subtitle tools instead of the bottom-bar Kotlin sources')
    parser.add_argument('--comment-menu', action='store_true',
                        help='Rebuild the comment menu fingerprints instead of bottom-bar hooks')
    parser.add_argument('--patch-source', type=Path, action='append',
                        help='Compile these Kotlin patch sources instead (repeat for multiple files)')
    parser.add_argument('--desktop-jar', type=Path,
                        default=Path.home() / '.local/share/morphe-desktop/morphe-desktop.jar')
    parser.add_argument('--output', type=Path, default=ROOT / 'extensions/tiktok/build/native-bottom-bar')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
    android = sdk / 'platforms/android-36/android.jar'
    d8 = sdk / 'build-tools/37.0.0/d8'
    compiler = artifact('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.10')
    annotations = artifact('org.jetbrains', 'annotations', '26.1.0')
    extension_output = output / 'extension'
    run([sys.executable, ROOT / 'scripts/build-local-extension.py', '--output', extension_output])
    with tempfile.TemporaryDirectory(prefix='patch-', dir=output) as temporary:
        work = Path(temporary)
        baseline = work / 'baseline.jar'
        shutil.copy2(args.cache, baseline)
        classes = work / 'classes'
        classes.mkdir()
        package = ROOT / 'patches/src/main/kotlin/app/morphe/patches/tiktok/interaction/videooverlays'
        sources = ([source.resolve() for source in args.patch_source] if args.patch_source else
                   [package.parents[1] / 'misc/commenttools/CommentMenuBlockHooks.kt'] if args.comment_menu else
                   [package.parent / 'captions/SubtitleToolsPatch.kt'] if args.subtitle_tools else
                   [package / 'NativeBottomBarHooks.kt', package / 'HideVideoOverlaysPatch.kt'])
        run(['java', '-cp', os.pathsep.join(map(str, [compiler, args.desktop_jar, annotations])),
             'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
             '-jvm-target', '17', '-module-name', 'morphe_patches',
             '-Xfriend-paths=' + str(baseline), '-classpath',
             os.pathsep.join(map(str, [baseline, args.desktop_jar])), '-d', classes,
             *sources])
        replacements = {p.relative_to(classes).as_posix(): p.read_bytes()
                        for p in classes.rglob('*.class')}
        expected = (None if args.patch_source else
                    '/CommentMenuItemsFingerprint.class' if args.comment_menu else
                    '/SubtitleToolsPatchKt.class' if args.subtitle_tools else '/NativeBottomBarHooksKt.class')
        if not replacements or expected and not any(name.endswith(expected) for name in replacements):
            raise RuntimeError('Native hook did not compile')
        with ZipFile(baseline) as original:
            names = original.namelist()
            if len(names) != len(set(names)) or names.count('extensions/tiktok.mpe') != 1:
                raise RuntimeError('Invalid baseline bundle entries')
            if any(n.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) for n in names):
                raise RuntimeError('Refusing to invalidate a signed bundle')
            with ZipFile(work / 'patch-program.jar', 'w', ZIP_DEFLATED) as program:
                for name in names:
                    if name.endswith('.class'):
                        program.writestr(name, replacements.get(name, original.read(name)))
                for name in replacements.keys() - set(names):
                    program.writestr(name, replacements[name])
            dex_output = work / 'dex'
            dex_output.mkdir()
            run([d8, '--release', '--min-api', '23', '--lib', android,
                 '--classpath', args.desktop_jar, '--output', dex_output, work / 'patch-program.jar'])
            dexes = list(dex_output.glob('*.dex'))
            if len(dexes) != 1:
                raise RuntimeError('Expected one patch DEX')
            replacements['classes.dex'] = dexes[0].read_bytes()
            replacements['extensions/tiktok.mpe'] = (extension_output / 'classes.dex').read_bytes()
            prepared = output / ('rebuilt-patches.mpp' if args.patch_source else
                                 'comment-menu.mpp' if args.comment_menu else
                                 'subtitle-tools.mpp' if args.subtitle_tools else 'native-bottom-bar.mpp')
            with ZipFile(prepared, 'w') as bundle:
                bundle.comment = original.comment
                for entry in original.infolist():
                    bundle.writestr(copy.copy(entry), replacements.get(entry.filename, original.read(entry)))
                for name in sorted(replacements.keys() - set(names)):
                    bundle.writestr(name, replacements[name], compress_type=ZIP_DEFLATED)
            with ZipFile(prepared) as bundle:
                if bundle.testzip() is not None:
                    raise RuntimeError('Prepared bundle failed CRC check')
                for name in names:
                    assert bundle.read(name) == replacements.get(name, original.read(name))
                for name, data in replacements.items():
                    assert bundle.read(name) == data
        if args.prepare_only:
            print('Prepared native patch JVM classes, patch DEX and extension:', prepared)
            return
        if args.cache.read_bytes() != baseline.read_bytes():
            raise RuntimeError('Cache changed during compilation; refusing to overwrite it')
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        backup = args.cache.with_name(args.cache.name + '.backup-' + stamp)
        shutil.copy2(args.cache, backup)
        handle, temporary_name = tempfile.mkstemp(prefix=args.cache.name + '.', dir=args.cache.parent)
        os.close(handle)
        staging = Path(temporary_name)
        try:
            shutil.copy2(prepared, staging)
            os.replace(staging, args.cache)
        finally:
            staging.unlink(missing_ok=True)
        print('Verified native patch JVM classes, patch DEX and extension:', args.cache)
        print('Backup:', backup)
        print('Prepared bundle:', prepared)


if __name__ == '__main__':
    main()
