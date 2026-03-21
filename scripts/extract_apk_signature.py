#!/usr/bin/env python3

import argparse
import hashlib
import os
import struct
import sys

APK_V2_BLOCK_ID = 0x7109871A


def extract_cert_info(apk_path: str) -> tuple[int, str]:
    with open(apk_path, "rb") as fp:
        data = fp.read()

    eocd_index = data.rfind(b"PK\x05\x06", max(0, len(data) - 0x10000 - 22))
    if eocd_index < 0:
        raise ValueError("EOCD not found")

    central_dir_offset = struct.unpack_from("<I", data, eocd_index + 16)[0]
    magic_index = data.rfind(
        b"APK Sig Block 42",
        max(0, central_dir_offset - 0x10000),
        central_dir_offset,
    )
    if magic_index < 0:
        raise ValueError("APK Sig Block 42 not found")

    block_size = struct.unpack_from("<Q", data, magic_index - 8)[0]
    pos = magic_index - block_size + 16
    end = magic_index - 16

    while pos < end:
        pair_size = struct.unpack_from("<Q", data, pos)[0]
        pair_id = struct.unpack_from("<I", data, pos + 8)[0]
        pair_start = pos + 12
        pair_end = pos + 8 + pair_size

        if pair_id == APK_V2_BLOCK_ID:
            signer_block = data[pair_start:pair_end]
            offset = 12
            digests_len = struct.unpack_from("<I", signer_block, offset)[0]
            offset += 4 + digests_len + 4
            cert_len = struct.unpack_from("<I", signer_block, offset)[0]
            offset += 4
            cert_data = signer_block[offset:offset + cert_len]
            if len(cert_data) != cert_len:
                raise ValueError("Certificate payload is truncated")
            return cert_len, hashlib.sha256(cert_data).hexdigest()

        pos = pair_end

    raise ValueError("APK v2 signing block not found")


def write_github_output(path: str, cert_size: int, cert_hash: str, package_name: str) -> None:
    with open(path, "a", encoding="utf-8") as fp:
        fp.write(f"cert_size={cert_size}\n")
        fp.write(f"cert_hash={cert_hash}\n")
        fp.write(f"package_name={package_name}\n")


def build_report(label: str, apk_path: str, package_name: str, cert_size: int, cert_hash: str) -> str:
    dynamic_manager = f"su -c \"echo '{cert_size}:{cert_hash}' > /data/adb/ksu/dynamic_manager\""
    lines = [
        f"Label: {label}",
        f"APK: {os.path.basename(apk_path)}",
        f"Package: {package_name}",
        f"Certificate Size: {cert_size}",
        f"Certificate SHA256: {cert_hash}",
        "Dynamic Manager:",
        dynamic_manager,
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract APK v2 certificate size and hash")
    parser.add_argument("apk", help="Path to the APK file")
    parser.add_argument("--package", dest="package_name", default="com.sukisu.ultra")
    parser.add_argument("--label", default="manager")
    parser.add_argument("--output", help="Write a human-readable report to this file")
    parser.add_argument("--github-output", dest="github_output", help="Append outputs for GitHub Actions")
    args = parser.parse_args()

    try:
        cert_size, cert_hash = extract_cert_info(args.apk)
    except Exception as exc:  # noqa: BLE001
        print(f"Failed to extract signing info: {exc}", file=sys.stderr)
        return 1

    report = build_report(args.label, args.apk, args.package_name, cert_size, cert_hash)
    sys.stdout.write(report)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as fp:
            fp.write(report)

    if args.github_output:
        write_github_output(args.github_output, cert_size, cert_hash, args.package_name)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
