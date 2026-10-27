import argparse
import json

from pathlib import Path
from typing import Any


class Arguments(argparse.Namespace):
    input: list[Path]
    inplace: bool
    percent: bool


total_time: dict[str, int] = {}

parser = argparse.ArgumentParser(description="Clean Spark sampler JSON file(s) into a streamlined format")
parser.add_argument("input", type=Path, nargs="+", help="Path to the Spark JSON file(s) to clean")
parser.add_argument("-i", "--inplace", action="store_true",
    help="Overwrite the input file instead of writing a sibling *.clean.json file",
)
parser.add_argument("-p", "--percent", action="store_true",
    help="Display times as percentages of the total time (\"69.42%%\")",
)
parser.add_argument("-s", "--strip-top", nargs="?", type=int, const=0,
    help="Strip the top-level nodes until we reach a node with multiple children. "
         "If an integer is provided, it specifies the number of top-level nodes to keep above the last stripped node."
)
parser.add_argument("-a", "--above", type=float,
    help="Keep only nodes above the specified percentage of the total time"
)


def expect_dict(value: Any, context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{context} must be a JSON object")

    return value


def expect_list(value: Any, context: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValueError(f"{context} must be a JSON array")

    return value


def expect_string(value: Any, context: str) -> str:
    if not isinstance(value, str):
        raise ValueError(f"{context} must be a string")

    return value


def expect_int(value: Any, context: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{context} must be an integer")

    return value


def expect_number(value: Any, context: str) -> int | float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{context} must be a number")

    return value


def expect_index(value: Any, size: int, context: str) -> int:
    index = expect_int(value, context)
    if index < 0 or index >= size:
        raise ValueError(f"{context} points outside the node pool: {index}")

    return index


def child_time_key(child: dict[str, Any]) -> int | float | str:
    t = child["time"]
    if isinstance(t, str) and t.endswith("%"):
        t = float(t.rstrip("%"))

    return t


def extract_time(record: dict[str, Any], context: str) -> int | float:
    if "times" in record and record["times"]:
        times = expect_list(record["times"], f"{context}.times")
        if not times:
            raise ValueError(f"{context}.times must not be empty")

        if len(times) == 1:
            return expect_number(times[0], f"{context}.times[0]")

        return expect_number(times[1], f"{context}.times[1]")

    if "time" in record:
        return expect_number(record["time"], f"{context}.time")

    raise ValueError(f"{context} is missing both time and times")


def build_reference(record: dict[str, Any], context: str) -> str:
    if "name" in record:
        return expect_string(record["name"], f"{context}.name")

    class_name = expect_string(record.get("className"), f"{context}.className")
    method_name = expect_string(record.get("methodName"), f"{context}.methodName")
    method_desc = expect_string(record.get("methodDesc"), f"{context}.methodDesc")
    line_number = expect_int(record.get("lineNumber"), f"{context}.lineNumber")

    return f"{class_name}.{method_name}{method_desc}#{line_number}"


def resolve_node(
    node_pool: list[Any],
    thread_index: int,
    node_index: int,
    node_cache: dict[int, dict[str, Any]],
    resolution_path: list[int],
    args: Arguments,
) -> dict[str, Any] | None:
    if node_index in node_cache:
        return node_cache[node_index]

    if node_index in resolution_path:
        cycle_path = " -> ".join(str(index) for index in [*resolution_path, node_index])
        raise ValueError(f"Detected cyclic childrenRefs: {cycle_path}")

    node = expect_dict(node_pool[node_index], f"children[{node_index}]")
    resolution_path.append(node_index)
    try:
        cleaned_node = clean_record(node, thread_index, f"children[{node_index}]", node_pool, node_cache, resolution_path, args)
    finally:
        resolution_path.pop()

    if cleaned_node is not None:
        node_cache[node_index] = cleaned_node

    return cleaned_node


def format_percentage(value: int, total: int) -> str:
    if value == total:
        return "100%"

    if total == 0:
        return "0%"

    return f"{(value / total) * 100:.2f}%"


def clean_record(
    record: dict[str, Any],
    thread_index: int,
    context: str,
    node_pool: list[Any] | None,
    node_cache: dict[int, dict[str, Any]],
    resolution_path: list[int],
    args: Arguments,
) -> dict[str, Any] | None:
    time_value = extract_time(record, context)
    reference = build_reference(record, context)
    children: list[dict[str, Any]] = []

    total_time_value = total_time.get(thread_index, 0)
    if args.above and total_time_value > 0 and (time_value / total_time_value) * 100 < args.above:
        return None

    if args.percent:
        time_value = format_percentage(time_value, total_time_value)

    if "childrenRefs" in record and record["childrenRefs"]:
        if node_pool is None:
            raise ValueError(f"{context}.childrenRefs requires a node pool")

        child_refs = expect_list(record["childrenRefs"], f"{context}.childrenRefs")
        for position, child_ref in enumerate(child_refs):
            child_index = expect_index(child_ref, len(node_pool), f"{context}.childrenRefs[{position}]")
            child = resolve_node(node_pool, thread_index, child_index, node_cache, resolution_path, args)
            if child is not None:
                children.append(child)
    else:
        child_values = expect_list(record.get("children", []), f"{context}.children")
        for position, child_value in enumerate(child_values):
            child_record = expect_dict(child_value, f"{context}.children[{position}]")
            child = clean_record(child_record, thread_index, f"{context}.children[{position}]", None,
                node_cache, resolution_path, args)
            if child is not None:
                children.append(child)

    children.sort(key=child_time_key, reverse=True)

    return {
        "time": time_value,
        "reference": reference,
        "children": children,
    }


def strip_top(thread: dict[str, Any], strip_count: int) -> dict[str, Any]:
    children = expect_list(thread.get("children", []), "thread.children")
    if len(children) != 1:
        return thread

    stripped = []

    current = thread
    current_children = expect_list(current.get("children", []), "thread.children")
    while len(current_children) == 1:
        stripped.append(current)
        current = expect_dict(current_children[0], "stripped.children[0]")
        current_children = expect_list(current.get("children", []), "stripped.children[0].children")

    stripped.append(current)

    if strip_count and len(stripped) > 1:
        strip_count = min(strip_count, len(stripped) - 2)
        stripped = stripped[:-strip_count]

    if stripped[-1] is not thread:
        thread["children"] = [stripped[-1]]

    return thread


def clean_thread(thread_value: Any, thread_index: int, args: Arguments) -> dict[str, Any]:
    thread = expect_dict(thread_value, f"threads[{thread_index}]")

    time_value = extract_time(thread, f"threads[{thread_index}]")
    if thread_index not in total_time:
        total_time[thread_index] = time_value

    # Spark stores each thread as a flat node pool and uses childrenRefs to rebuild the tree.
    node_pool = expect_list(thread.get("children", []), f"threads[{thread_index}].children")
    node_cache: dict[int, dict[str, Any]] = {}
    cleaned_thread = clean_record(thread, thread_index, f"threads[{thread_index}]", node_pool, node_cache, [], args)
    if args.strip_top is not None:
        return strip_top(cleaned_thread, args.strip_top)

    return cleaned_thread


def clean_sources(sources: dict) -> list | dict:
    if not isinstance(sources, dict):
        return sources

    new_sources: list[str] = []
    for data in sources.values():
        name = data.get("name")
        version = data.get("version")

        if name is not None and version is not None:
            version_short = version.split("|")[0].strip()
            new_sources.append(f"{name} {version_short}")

    new_sources.sort()

    return new_sources


def clean_document(document: Any, args: Arguments) -> Any:
    if isinstance(document, dict) and "threads" in document:
        thread_values = expect_list(document["threads"], "threads")
        if not thread_values:
            raise ValueError("No time data found in the document")

        document["threads"] = [
            clean_thread(thread_value, thread_index, args)
            for thread_index, thread_value in enumerate(thread_values)
        ]

        if "metadata" in document and "sources" in document["metadata"]:
            document["metadata"]["sources"] = clean_sources(document["metadata"]["sources"])

        return document

    if isinstance(document, list):
        return [
            clean_thread(thread_value, thread_index, args)
            for thread_index, thread_value in enumerate(document)
        ]

    return clean_thread(document, 0, args)


def main(argv: list[str] | None = None) -> int:
    args: Arguments = parser.parse_args(argv, Arguments)

    input_paths = [path.expanduser() for path in args.input]
    if args.inplace:
        output_paths = input_paths
    else:
        output_paths = [input_path.with_name(f"{input_path.stem}.clean{input_path.suffix}") for input_path in input_paths]

    for input_path, output_path in zip(input_paths, output_paths):
        total_time.clear()

        try:
            cleaned_document = clean_document(json.loads(input_path.read_text("utf-8")), args)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_text(json.dumps(cleaned_document, indent=2))

            print(f"Wrote cleaned JSON to {output_path}")
        except Exception as e:
            print(f"Failed to clean {input_path}: {e}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())