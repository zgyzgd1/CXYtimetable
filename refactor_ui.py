#!/usr/bin/env python3
"""Retired one-off UI refactor helper.

The previous version rewrote files from hardcoded line numbers. Keeping this
entry point as a safe failure makes accidental runs harmless while preserving a
clear breadcrumb for anyone who finds older documentation.
"""

import argparse


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Retired one-off ScheduleScreen refactor helper.",
    )
    parser.add_argument(
        "--source",
        required=True,
        help="Former source file argument; retained only to avoid implicit paths.",
    )
    parser.parse_args()
    parser.exit(
        status=2,
        message=(
            "refactor_ui.py is retired because the old implementation depended "
            "on hardcoded line ranges. Use normal IDE refactors or git history "
            "instead.\n"
        ),
    )


if __name__ == "__main__":
    raise SystemExit(main())
