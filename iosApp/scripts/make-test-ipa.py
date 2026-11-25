#!/usr/bin/env python3
"""Create a separately installable layatest variant from the built Laya IPA."""
import argparse
import hashlib
import plistlib
import subprocess
import tempfile
import zipfile
from pathlib import Path


def update_plist(path, update):
    with path.open("rb") as stream:
        value = plistlib.load(stream)
    update(value)
    with path.open("wb") as stream:
        plistlib.dump(value, stream, fmt=plistlib.FMT_BINARY)


def make_variant(source, destination):
    app_id = "com.music.layatest"
    group = "group." + app_id
    with tempfile.TemporaryDirectory(prefix="layatest-ipa-") as folder:
        stage = Path(folder)
        with zipfile.ZipFile(source) as archive:
            for entry in archive.infolist():
                target = (stage / entry.filename).resolve()
                if not target.is_relative_to(stage.resolve()):
                    raise ValueError("IPA contains a path outside its payload")
        subprocess.run(["/usr/bin/ditto", "-x", "-k", str(source.resolve()), str(stage)], check=True)
        apps = list((stage / "Payload").glob("*.app"))
        if len(apps) != 1:
            raise ValueError("Expected one iOS application")
        app = apps[0].rename(stage / "Payload/layatest.app")

        def identity(info):
            info.update(CFBundleIdentifier=app_id, CFBundleDisplayName="layatest", CFBundleName="layatest",
                        LayaURLScheme="layatest", LayaAppGroup=group)
            for item in info.get("CFBundleURLTypes", []):
                item["CFBundleURLName"] = app_id + ".party"
                item["CFBundleURLSchemes"] = ["layatest"]
        update_plist(app / "Info.plist", identity)
        widget = app / "PlugIns/LayaWidgets.appex"
        update_plist(widget / "Info.plist", lambda info: info.update(
            CFBundleIdentifier=app_id + ".widgets", CFBundleDisplayName="layatest",
            LayaURLScheme="layatest", LayaAppGroup=group))

        entitlements = stage / "test-entitlements.plist"
        with entitlements.open("wb") as stream:
            plistlib.dump({"com.apple.security.application-groups": [group]}, stream)
        for bundle in [widget, app]:
            subprocess.run(["codesign", "--force", "--sign", "-", "--timestamp=none",
                            "--generate-entitlement-der", "--entitlements", str(entitlements), str(bundle)], check=True)
            subprocess.run(["codesign", "--verify", "--strict", str(bundle)], check=True)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.unlink(missing_ok=True)
        subprocess.run(["/usr/bin/zip", "-qry", str(destination.resolve()), "Payload"], cwd=stage, check=True)

    digest = hashlib.sha256()
    with destination.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    destination.with_suffix(".ipa.sha256").write_text(digest.hexdigest() + "  " + destination.name + "\n")
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise ValueError("IPA failed CRC verification")
    print(destination)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    make_variant(args.source, args.destination)
