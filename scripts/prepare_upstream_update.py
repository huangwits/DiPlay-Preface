"""Prepare a local review branch; never push or change main."""
import argparse
import re
import subprocess


def git(*args, check=True):
    result = subprocess.run(["git", *args], text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result


def prepare(source, commit=None):
    if git("status", "--porcelain").stdout.strip():
        raise RuntimeError("A clean working tree is required")
    start = git("rev-parse", "HEAD").stdout.strip()
    original_branch = git("symbolic-ref", "--short", "HEAD").stdout.strip()
    if source == "geely":
        target = git("rev-parse", "geely/main").stdout.strip()
        if git("merge-base", "--is-ancestor", target, start, check=False).returncode == 0:
            return None
        operation = ["merge", "--no-ff", "--no-edit", target]
    elif source == "original":
        if not commit or not re.fullmatch(r"[0-9a-fA-F]{40}", commit):
            raise ValueError("Direct original-author updates require one reviewed full commit SHA")
        target = git("rev-parse", commit + "^{commit}").stdout.strip()
        if git("merge-base", "--is-ancestor", target, "upstream/main", check=False).returncode:
            raise ValueError("The selected commit is not in upstream/main")
        if len(git("show", "-s", "--format=%P", target).stdout.split()) != 1:
            raise ValueError("Select a single non-merge original-author commit, not a branch merge")
        history = git("log", "--format=%B").stdout
        if git("merge-base", "--is-ancestor", target, start, check=False).returncode == 0 or (
            "cherry picked from commit " + target in history
        ):
            return None
        operation = ["cherry-pick", "-x", target]
    else:
        raise ValueError("Unknown source")
    branch = f"sync/{source}-{target[:12]}"
    git("switch", "-c", branch)
    result = git(*operation, check=False)
    if result.returncode:
        conflicts = git("diff", "--name-only", "--diff-filter=U").stdout.strip()
        git(operation[0], "--abort", check=False)
        git("switch", original_branch)
        raise RuntimeError("Update needs manual resolution; main is unchanged.\n" + conflicts + "\n" + result.stderr)
    return branch


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=["geely", "original"], default="geely")
    parser.add_argument("--commit")
    args = parser.parse_args()
    branch = prepare(args.source, args.commit)
    print(branch or "Already integrated")
