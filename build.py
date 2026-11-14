#!/usr/bin/env python3
"""Build the FcWatch LSPosed module APK without Gradle.

aapt2 compile -> aapt2 link -> javac -> d8 -> repack -> zipalign -> apksigner
"""
from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", r"D:\Envs\Android\Sdk"))
BT = SDK / "build-tools" / "35.0.0"
ANDROID = SDK / "platforms" / "android-35" / "android.jar"

APP_ID = "com.junjun.sideback"
VERSION_CODE = "3"
VERSION_NAME = "1.2"
MIN_SDK = "29"
TARGET_SDK = "35"
XPOSED_ENTRY = "com.junjun.sideback.SideBackModule"
MODULE_PROP = ("minApiVersion=100\n"
               "targetApiVersion=102\n"
               "staticScope=true\n"
               "name=SideBackStyle\n"
               "version=1.2\n"
               "versionCode=3\n"
               "author=Jun\n"
               "description=ColorOS 侧滑返回自定义图标与背景\n")

WORK = Path(tempfile.gettempdir()) / "sideback-build"
STAGE = WORK / "src"
OUT = WORK / "out"
DIST = ROOT / "dist"
VENDOR = ROOT / "vendor"
MAIN = ROOT / "app" / "src" / "main"


def run(*args, env=None):
    args = [str(a) for a in args]
    print("+", " ".join(args))
    subprocess.run(args, check=True, env=env)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def find_jdk() -> Path:
    candidates = [
        os.environ.get("JAVA_HOME"),
        r"D:\Program Files\Android\Android Studio\jbr",
        r"D:\Envs\Android\Sdk\jbr",
    ]
    for candidate in candidates:
        if not candidate:
            continue
        base = Path(candidate)
        if (base / "bin" / "javac.exe").is_file():
            return base
    raise FileNotFoundError("no JDK with javac found; set JAVA_HOME")


JAVA_HOME = find_jdk()
JAVA = JAVA_HOME / "bin" / "java.exe"
JAVAC = JAVA_HOME / "bin" / "javac.exe"
KEYTOOL = JAVA_HOME / "bin" / "keytool.exe"


def add(zf: zipfile.ZipFile, name: str, data: bytes, compress: int) -> None:
    info = zipfile.ZipInfo(name)
    info.compress_type = compress
    info.external_attr = 0o644 << 16
    zf.writestr(info, data)


def compile_java(sources, classpath, out_dir: Path) -> None:
    last = None
    for release in ("11", "17"):
        try:
            run(
                JAVAC, "-encoding", "UTF-8", "--release", release,
                "-classpath", os.pathsep.join(str(p) for p in classpath),
                "-d", out_dir, *sources,
            )
            return
        except subprocess.CalledProcessError as exc:
            last = exc
            print(f"javac --release {release} failed, retrying")
    raise last


def main() -> None:
    for path in (ANDROID, BT / "aapt2.exe"):
        if not path.exists():
            raise FileNotFoundError(f"missing Android SDK component: {path}")
    if not JAVAC.exists():
        raise FileNotFoundError(f"missing javac: {JAVAC}")

    xp_jar = WORK / "libxposed-api.jar"
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    DIST.mkdir(parents=True, exist_ok=True)
    if STAGE.exists():
        shutil.rmtree(STAGE, ignore_errors=True)
    shutil.copytree(MAIN, STAGE)
    shutil.copyfile(VENDOR / "libxposed-api.jar", xp_jar)

    aapt2 = BT / "aapt2.exe"
    zipalign = BT / "zipalign.exe"

    run(aapt2, "compile", "--dir", STAGE / "res", "-o", OUT / "res.zip")
    run(
        aapt2, "link",
        "-o", OUT / "base.apk",
        "-I", ANDROID,
        "-A", STAGE / "assets",
        "--java", OUT / "gen",
        "--manifest", STAGE / "AndroidManifest.xml",
        "--min-sdk-version", MIN_SDK,
        "--target-sdk-version", TARGET_SDK,
        "--version-code", VERSION_CODE,
        "--version-name", VERSION_NAME,
        OUT / "res.zip",
    )

    classes_dir = OUT / "classes"
    classes_dir.mkdir()
    sources = sorted((STAGE / "java").rglob("*.java")) + sorted((OUT / "gen").rglob("*.java"))
    compile_java(sources, [ANDROID, xp_jar], classes_dir)

    classes = sorted(classes_dir.rglob("*.class"))
    args_file = OUT / "d8.args"
    args_file.write_text(
        "\n".join(
            f'"{str(a).replace(chr(92), "/")}"'
            for a in [
                "-cp", BT / "lib" / "d8.jar", "com.android.tools.r8.D8",
                "--min-api", MIN_SDK,
                "--lib", ANDROID,
                "--classpath", xp_jar,
                "--output", OUT / "dex",
                *classes,
            ]
        ),
        encoding="utf-8",
    )
    (OUT / "dex").mkdir(exist_ok=True)
    run(JAVA, "@" + str(args_file))

    with zipfile.ZipFile(OUT / "base.apk") as base, zipfile.ZipFile(
        OUT / "unsigned.apk", "w", zipfile.ZIP_DEFLATED, compresslevel=9
    ) as z:
        for info in base.infolist():
            if info.is_dir():
                continue
            add(z, info.filename, base.read(info), info.compress_type)
        add(z, "classes.dex", (OUT / "dex" / "classes.dex").read_bytes(), zipfile.ZIP_DEFLATED)
        add(z, "META-INF/xposed/java_init.list", (XPOSED_ENTRY + "\n").encode(), zipfile.ZIP_DEFLATED)
        add(z, "META-INF/xposed/module.prop", MODULE_PROP.encode(), zipfile.ZIP_DEFLATED)
        add(z, "META-INF/xposed/scope.list", b"com.android.systemui\n", zipfile.ZIP_DEFLATED)

    run(zipalign, "-f", "-p", "4", OUT / "unsigned.apk", OUT / "aligned.apk")

    keystore = Path.home() / ".android" / "debug.keystore"
    if not keystore.is_file():
        keystore.parent.mkdir(parents=True, exist_ok=True)
        env = {**os.environ, "FC_KS_PASS": "android"}
        run(
            KEYTOOL, "-genkeypair", "-keystore", keystore,
            "-storepass:env", "FC_KS_PASS", "-keypass:env", "FC_KS_PASS",
            "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000", "-dname", "CN=SideBackStyle Debug",
            env=env,
        )

    apk = DIST / f"SideBackStyle-{VERSION_NAME}.apk"
    env = {**os.environ, "FC_KS_PASS": "android"}
    run(
        JAVA, "-jar", BT / "lib" / "apksigner.jar", "sign",
        "--alignment-preserved", "true",
        "--ks", keystore,
        "--ks-pass", "env:FC_KS_PASS",
        "--key-pass", "env:FC_KS_PASS",
        "--ks-key-alias", "androiddebugkey",
        "--out", apk,
        OUT / "aligned.apk",
        env=env,
    )
    run(JAVA, "-jar", BT / "lib" / "apksigner.jar", "verify", "--verbose", apk)
    run(zipalign, "-c", "-p", "4", apk)

    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        for required in ("AndroidManifest.xml", "classes.dex", "resources.arsc",
                         "META-INF/xposed/java_init.list", "META-INF/xposed/module.prop"):
            if required not in names:
                raise ValueError(f"APK missing {required}")
        entry = z.read("META-INF/xposed/java_init.list").decode("utf-8").strip()
        if entry != XPOSED_ENTRY:
            raise ValueError(f"bad java_init.list: {entry}")

    print("\nbuild ok")
    print(f"  APK   : {apk}")
    print(f"  size  : {apk.stat().st_size} bytes")
    print(f"  sha256: {sha256(apk)}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        print(f"\nbuild failed: exit {exc.returncode}", file=sys.stderr)
        raise SystemExit(1)


