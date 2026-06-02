#!/usr/bin/env python3

import argparse
import bisect
import operator
import re
import sys

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class CompactionCall:
    a: str
    b: str
    c: int
    statement_end_index: int


COMPACTION_CALL_PATTERN = re.compile(
    r"mods\s*\.\s*storagedrawers\s*\.\s*Compaction\s*\.\s*add",
    flags=re.MULTILINE,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Recursively scan files for mods.storagedrawers.Compaction.add(A, B, C) "
            "and add matching shapeless compression/decompression recipes under each call."
        )
    )

    parser.add_argument("directory", help="Directory to scan recursively")
    parser.add_argument("--dry", action="store_true", help="Do not write files, print what would change")

    return parser.parse_args()


def normalize_whitespace(value: str) -> str:
    return "".join(value.split())


def char_index_to_line_index(newline_positions: list[int], char_index: int) -> int:
    return bisect.bisect_right(newline_positions, char_index)


def find_newline_positions(text: str) -> list[int]:
    newline_positions: list[int] = []
    for index, char in enumerate(text):
        if char == "\n":
            newline_positions.append(index)

    return newline_positions


def is_likely_text_file(file_path: Path) -> bool:
    try:
        with open(file_path, "rb") as handle:
            chunk = handle.read(4096)

        return b"\x00" not in chunk
    except OSError:
        return False


def read_text_file(file_path: Path) -> str | None:
    try:
        return file_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        print(f"Skipping non-utf8 file: {file_path}", file=sys.stderr)
        return None
    except OSError as exc:
        print(f"Skipping unreadable file: {file_path} ({exc})", file=sys.stderr)
        return None


def parse_compaction_calls(text: str) -> list[CompactionCall]:
    """
    Parse calls to mods.storagedrawers.Compaction.add in the given text, returning a list of CompactionCall.
    The parser doesn't use a regex due to the complex nature of ZS arguments (NBT tags).
    """

    calls: list[CompactionCall] = []

    search_index = 0
    while True:
        match = COMPACTION_CALL_PATTERN.search(text, search_index)
        if match is None:
            break

        start_index = match.start()
        index = match.end()
        while index < len(text) and text[index].isspace():
            index += 1

        if index >= len(text) or text[index] != "(":
            search_index = start_index + 1
            continue

        index += 1
        stack = ["("]
        args_text = ""
        in_string = False
        string_quote = ""
        escape = False

        while index < len(text) and stack:
            char = text[index]

            if in_string:
                args_text += char
                if escape:
                    escape = False
                elif char == "\\":
                    escape = True
                elif char == string_quote:
                    in_string = False
                index += 1
                continue

            if char in ("\"", "'"):
                in_string = True
                string_quote = char
                args_text += char
                index += 1
                continue

            if char in "([{":
                stack.append(char)
                args_text += char
                index += 1
                continue

            if char in ")]}":
                opener = stack[-1]
                if (opener, char) in (("(", ")"), ("[", "]"), ("{", "}")):
                    stack.pop()
                    if stack:
                        args_text += char
                    index += 1
                    continue

                search_index = start_index + 1
                break

            args_text += char
            index += 1

        if stack:
            search_index = start_index + 1
            continue

        while index < len(text) and text[index].isspace():
            index += 1

        if index >= len(text) or text[index] != ";":
            search_index = start_index + 1
            continue

        statement_end_index = index

        args = split_top_level_args(args_text)
        if len(args) != 3:
            search_index = start_index + 1
            continue

        a = args[0].strip()
        b = args[1].strip()
        c_raw = args[2].strip()
        try:
            c = int(c_raw)
        except ValueError:
            print(
                "Skipping Compaction.add with non-integer ratio "
                f"({c_raw}): mods.storagedrawers.Compaction.add({args_text});",
                file=sys.stderr
            )
            search_index = start_index + 1
            continue

        calls.append(CompactionCall(a=a, b=b, c=c, statement_end_index=statement_end_index))
        search_index = statement_end_index + 1

    return calls


def split_top_level_args(args_text: str) -> list[str]:
    args: list[str] = []
    current = ""

    stack: list[str] = []
    in_string = False
    string_quote = ""
    escape = False

    for char in args_text:
        if in_string:
            current += char
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == string_quote:
                in_string = False
            continue

        if char in ("\"", "'"):
            in_string = True
            string_quote = char
            current += char
            continue

        if char in "([{":
            stack.append(char)
            current += char
            continue

        if char in ")]}":
            if not stack:
                current += char
                continue

            opener = stack[-1]
            if (opener, char) in (("(", ")"), ("[", "]"), ("{", "}")):
                stack.pop()
            current += char
            continue

        if char == "," and not stack:
            args.append(current)
            current = ""
            continue

        current += char

    if current:
        args.append(current)

    return args


def build_recipe_lines(a: str, b: str, c: int, indent: str, newline: str) -> list[str]:
    decompression = f"{indent}recipes.addShapeless({b}*{c}, [{a}]);{newline}"
    compression_inputs = ", ".join([b] * c)
    compression = f"{indent}recipes.addShapeless({a}, [{compression_inputs}]);{newline}"

    return [decompression, compression]


def process_file(file_path: Path, root_dir: Path, dry: bool) -> None:
    if not is_likely_text_file(file_path):
        return

    original_text = read_text_file(file_path)
    if original_text is None:
        return

    if COMPACTION_CALL_PATTERN.search(original_text) is None:
        return

    calls = parse_compaction_calls(original_text)
    if not calls:
        return

    # Handle newlines manually to preserve the original file's newline style
    newline = "\r\n" if "\r\n" in original_text else "\n"
    lines = original_text.splitlines(keepends=True)
    newline_positions = find_newline_positions(original_text)

    existing_normalized_lines: set[str] = set()
    for line in lines:
        existing_normalized_lines.add(normalize_whitespace(line.rstrip("\r\n")))

    calls_with_line_index: list[tuple[int, CompactionCall]] = []
    for call in calls:
        line_index = char_index_to_line_index(newline_positions, call.statement_end_index)
        if line_index < 0 or line_index >= len(lines):
            continue

        calls_with_line_index.append((line_index, call))

    if not calls_with_line_index:
        return

    calls_with_line_index.sort(key=operator.itemgetter(0), reverse=True)

    wrote_count = 0
    skipped_count = 0
    recipes_for_log: list[str] = []

    for line_index, call in calls_with_line_index:
        statement_line = lines[line_index]
        indent = statement_line[: len(statement_line) - len(statement_line.lstrip())]
        recipe_lines = build_recipe_lines(call.a, call.b, call.c, indent, newline)

        expected_1 = recipe_lines[0].rstrip("\r\n")
        expected_2 = recipe_lines[1].rstrip("\r\n")
        expected_1_normalized = normalize_whitespace(expected_1)
        expected_2_normalized = normalize_whitespace(expected_2)

        next_1 = lines[line_index + 1] if line_index + 1 < len(lines) else ""
        next_2 = lines[line_index + 2] if line_index + 2 < len(lines) else ""
        next_1_normalized = normalize_whitespace(next_1.rstrip("\r\n"))
        next_2_normalized = normalize_whitespace(next_2.rstrip("\r\n"))

        expected_1_exists = expected_1_normalized in existing_normalized_lines
        expected_2_exists = expected_2_normalized in existing_normalized_lines
        if expected_1_exists:
            skipped_count += 1
        if expected_2_exists:
            skipped_count += 1

        if next_1_normalized == expected_1_normalized:
            if expected_2_exists:
                continue

            insert_at = line_index + 2
            if dry:
                wrote_count += 1
                recipes_for_log.append(expected_2.strip())
                existing_normalized_lines.add(expected_2_normalized)
                continue

            lines[insert_at:insert_at] = [recipe_lines[1]]
            wrote_count += 1
            recipes_for_log.append(expected_2.strip())
            existing_normalized_lines.add(expected_2_normalized)
            continue

        if next_1_normalized == expected_2_normalized:
            if expected_1_exists:
                continue

            insert_at = line_index + 1
            if dry:
                wrote_count += 1
                recipes_for_log.append(expected_1.strip())
                existing_normalized_lines.add(expected_1_normalized)
                continue

            lines[insert_at:insert_at] = [recipe_lines[0]]
            wrote_count += 1
            recipes_for_log.append(expected_1.strip())
            existing_normalized_lines.add(expected_1_normalized)
            continue

        if next_2_normalized == expected_1_normalized and next_1_normalized == expected_2_normalized:
            continue

        insert_at = line_index + 1
        insert_lines: list[str] = []
        if not expected_1_exists:
            insert_lines.append(recipe_lines[0])
        if not expected_2_exists:
            insert_lines.append(recipe_lines[1])

        if not insert_lines:
            continue

        if dry:
            if not expected_1_exists:
                wrote_count += 1
                recipes_for_log.append(expected_1.strip())
                existing_normalized_lines.add(expected_1_normalized)

            if not expected_2_exists:
                wrote_count += 1
                recipes_for_log.append(expected_2.strip())
                existing_normalized_lines.add(expected_2_normalized)

            continue

        lines[insert_at:insert_at] = insert_lines
        if not expected_1_exists:
            wrote_count += 1
            recipes_for_log.append(expected_1.strip())
            existing_normalized_lines.add(expected_1_normalized)

        if not expected_2_exists:
            wrote_count += 1
            recipes_for_log.append(expected_2.strip())
            existing_normalized_lines.add(expected_2_normalized)

    try:
        relative_path = file_path.relative_to(root_dir)
    except ValueError:
        relative_path = file_path

    if dry:
        print(
            f"Would have written {wrote_count} compression recipes for {relative_path} "
            f"(skipped {skipped_count} already present) :"
        )
        for recipe in recipes_for_log:
            print(f"- {recipe}")

        print()

        return

    if wrote_count > 0:
        new_text = "".join(lines)
        try:
            file_path.write_text(new_text, encoding="utf-8", newline="")
        except OSError as exc:
            print(f"Failed to write file: {file_path} ({exc})", file=sys.stderr)
            return

    print(
        f"Wrote {wrote_count} compression recipes for {relative_path} "
        f"(skipped {skipped_count} already present)\n"
    )


def iter_files_recursively(root_dir: Path) -> list[Path]:
    root_path = root_dir
    file_paths: list[Path] = []
    for candidate in root_path.rglob("*"):
        if not candidate.is_file():
            continue

        try:
            relative = candidate.relative_to(root_path)
        except ValueError:
            continue

        if any(part.startswith(".") for part in relative.parts):
            continue

        file_paths.append(candidate)

    return file_paths


def main() -> int:
    args = parse_args()
    root_dir = Path(args.directory).expanduser().resolve()
    if not root_dir.is_dir():
        raise NotADirectoryError(f"Not a directory: {root_dir}")

    file_paths = iter_files_recursively(root_dir)
    try:
        for file_path in file_paths:
            process_file(file_path=file_path, root_dir=root_dir, dry=args.dry)
    except BrokenPipeError:
        return 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
