#!/usr/bin/env python3
"""Compares the public/protected API of our framework classes with android.jar.

Usage: tools/api_check.py [-v] android.view.View android.view.ViewGroup ...
       tools/api_check.py -p android.view   (every class of a package that we have)

Prints members present in android.jar but missing from build/java/framework
(the ones the VM would silently auto-stub) and, with -v, members we declare
that android.jar does not have (often typos). Static-ness, parameter types and
return types are compared; modifiers such as final, synchronized, native and
throws clauses are ignored. Inherited members count as present.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID_JAR = os.path.join(ROOT, "build/toolchains/sdk/android.jar")
OURS = os.path.join(ROOT, "build/java/framework") + os.pathsep + os.path.join(ROOT, "build/java/libcore")

DROP = re.compile(r"\b(final|synchronized|native|transient|volatile|strictfp|default|abstract)\s+")
GENERIC = re.compile(r"<[^<>]*>")


def javap(cp, classes):
    if not classes:
        return ""
    cmd = ["javap", "-protected", "-cp", cp] + classes
    out = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return out.stdout


def strip_generics(s):
    prev = None
    while prev != s:
        prev = s
        s = GENERIC.sub("", s)
    return s


def normalize(line, cls_simple):
    line = line.strip().rstrip(";")
    if not line or line.startswith("Compiled from") or line == "}" or line.endswith("{"):
        return None
    line = re.sub(r"\s+throws\s+.*$", "", line)
    line = DROP.sub("", line)
    line = strip_generics(line)
    line = line.replace("...", "[]")
    return re.sub(r"\s+", " ", line)


def parse(text):
    """Returns {class: set(members)} and {class: header line}."""
    classes, headers = {}, {}
    cur = None
    for raw in text.splitlines():
        s = raw.strip()
        if s.endswith("{") and ("class " in s or "interface " in s):
            m = re.search(r"(?:class|interface)\s+([\w.$]+)", s)
            cur = m.group(1)
            classes[cur] = set()
            headers[cur] = strip_generics(s)
            continue
        if cur is None:
            continue
        n = normalize(raw, cur.split(".")[-1])
        if n:
            # drop the parameter names that some javap versions print? (they do not) keep as is
            n = n.replace(cur + "(", "<init>(")
            classes[cur].add(n)
    return classes, headers


def superclass_chain(header):
    m = re.search(r"extends\s+([\w.$]+)", header)
    return m.group(1) if m and "class " in header else None


def list_package(cp_dir, pkg):
    d = os.path.join(cp_dir, pkg.replace(".", "/"))
    if not os.path.isdir(d):
        return []
    return sorted(pkg + "." + f[:-6] for f in os.listdir(d) if f.endswith(".class"))


def main():
    args = sys.argv[1:]
    verbose = "-v" in args
    args = [a for a in args if a != "-v"]
    names = []
    i = 0
    while i < len(args):
        if args[i] == "-p":
            names += list_package(os.path.join(ROOT, "build/java/framework"), args[i + 1])
            i += 2
        else:
            names.append(args[i])
            i += 1
    names = [n for n in names if not re.search(r"\$\d", n)]
    ref_text = javap(ANDROID_JAR, names)
    ref, ref_headers = parse(ref_text)
    ours_text = javap(OURS, names)
    ours, our_headers = parse(ours_text)

    def our_members(cls, seen=None):
        """Our members including inherited ones from our superclasses."""
        seen = seen or set()
        if cls in seen:
            return set()
        seen.add(cls)
        if cls not in ours:
            t, h = parse(javap(OURS, [cls]))
            ours.update(t)
            our_headers.update(h)
        members = set(ours.get(cls, set()))
        sup = superclass_chain(our_headers.get(cls, ""))
        if sup:
            simple = sup.split(".")[-1]
            for m in our_members(sup, seen):
                if "<init>(" not in m:
                    members.add(m)
        return members

    total = 0
    for cls in names:
        if cls not in ref:
            if verbose:
                print("%s: not in android.jar" % cls)
            continue
        if cls not in ours:
            print("%s: MISSING CLASS" % cls)
            total += 1
            continue
        mine = our_members(cls)
        missing = sorted(m for m in ref[cls] if m not in mine)
        extra = sorted(m for m in ours[cls] if m not in ref[cls]) if verbose else []
        if missing or extra:
            print("== %s: %d missing%s" % (cls, len(missing), (", %d extra" % len(extra)) if verbose else ""))
            for m in missing:
                print("  - " + m)
            for m in extra:
                print("  + " + m)
        total += len(missing)
    print("total missing: %d" % total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
