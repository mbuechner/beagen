import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


BUILD_INPUTS_LABEL = "de.ddb.labs.beagen.build-inputs"
TAGGED_IMAGE = re.compile(r"[a-zA-Z0-9][a-zA-Z0-9./_-]*:[a-zA-Z0-9_.-]+")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")


def inspect(image, *options, allow_missing=False):
    result = subprocess.run(
        ["docker", "buildx", "imagetools", "inspect", *options, image],
        capture_output=True,
    )
    if result.returncode:
        error = result.stderr.decode(errors="replace").strip()
        if allow_missing and (
            f"{image.lower()}: not found" in error.lower() or "manifest unknown" in error.lower()
        ):
            return None
        raise SystemExit(error or f"Could not inspect {image}.")
    return result.stdout


def base_images(dockerfile):
    images = []
    for line in dockerfile.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields or fields[0].upper() != "FROM":
            continue
        if len(fields) < 2 or not TAGGED_IMAGE.fullmatch(fields[1]):
            return []
        images.append(fields[1])
    return images


def linux_amd64_digest(image):
    raw_manifest = inspect(image, "--raw")
    manifest = json.loads(raw_manifest)
    if "manifests" not in manifest:
        return "sha256:" + hashlib.sha256(raw_manifest).hexdigest()

    for entry in manifest["manifests"]:
        platform = entry.get("platform", {})
        if platform.get("os") == "linux" and platform.get("architecture") == "amd64":
            digest = entry["digest"]
            if DIGEST.fullmatch(digest):
                return digest
    raise SystemExit(f"No linux/amd64 digest found for {image}.")


def main():
    names = base_images(Path("release-source/Dockerfile"))
    if not names:
        print("Building normally because the Dockerfile lacks literal base image tags.")
        return

    bases = [(name, linux_amd64_digest(name)) for name in dict.fromkeys(names)]
    inputs = json.dumps([os.environ["RELEASE_REVISION"], bases]).encode()
    fingerprint = hashlib.sha256(inputs).hexdigest()
    release_image = f"{os.environ['IMAGE']}:{os.environ['RELEASE_TAG']}"
    published = inspect(release_image, "--format", "{{json .}}", allow_missing=True)

    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        if published is not None:
            details = json.loads(published)
            image = details.get("image") or {}
            config = image.get("config") or {}
            labels = config.get("Labels") or {}
            if labels.get(BUILD_INPUTS_LABEL) == fingerprint:
                digest = details["manifest"]["digest"]
                if not DIGEST.fullmatch(digest):
                    raise SystemExit(f"Invalid digest for {release_image}.")
                print(f"reusable_digest={digest}", file=output)
                print(f"Reusing {release_image} with unchanged build inputs.")
                return

        runtime = names[-1]
        print(f"base_name={runtime}", file=output)
        print(f"base_digest={dict(bases)[runtime]}", file=output)
        print(f"fingerprint={fingerprint}", file=output)
        print("contexts<<BASE_IMAGES", file=output)
        for name, digest in bases:
            print(f"{name}=docker-image://{name}@{digest}", file=output)
        print("BASE_IMAGES", file=output)


if __name__ == "__main__":
    main()
