from pathlib import Path

import pytest

import dataset_tools as dt
import prepare_dataset


def test_parse_label_file():
    boxes, errors = dt.parse_label_file("0 0.5 0.5 0.2 0.1\n\n0 0.1 0.1 0.1 0.1\n")
    assert len(boxes) == 2 and errors == []

    _, errors = dt.parse_label_file("0 0.5 0.5 0.2\n1 0.5 0.5 0.2 0.1\n0 a 0.5 0.2 0.1\n"
                                    "0 1.5 0.5 0.2 0.1\n0 0.98 0.5 0.2 0.1\n0 0.5 0.5 0 0.1\n")
    assert [e.split(":")[0] for e in errors] == [f"línea {n}" for n in range(1, 7)]
    assert "clase 1" in errors[1]
    assert "se sale" in errors[4]


def test_group_of():
    assert dt.group_of(Path("ABC123_01.jpg"), r"^(\w+?)_") == "ABC123"
    assert dt.group_of(Path("foto.jpg"), r"^(\w+?)_") == "foto"
    assert dt.group_of(Path("foto.jpg"), None) == "foto"


def _samples(groups):
    return [dt.Sample(Path(f"{g}_{i}.jpg"), None, g) for g, n in groups for i in range(n)]


def test_split_keeps_groups_together_and_is_reproducible():
    samples = _samples([(f"V{i}", 3) for i in range(20)])
    a = dt.split_groups(samples, (0.7, 0.2, 0.1), seed=1)
    b = dt.split_groups(samples, (0.7, 0.2, 0.1), seed=1)
    assert a == b
    assert sum(len(v) for v in a.values()) == 60
    seen = {}
    for split, items in a.items():
        for s in items:
            assert seen.setdefault(s.group, split) == split
    assert len(a["train"]) >= 42 and a["val"] and a["test"]


def test_split_small_dataset_fills_every_split():
    parts = dt.split_groups(_samples([(f"V{i}", 2) for i in range(12)]), (0.7, 0.2, 0.1), seed=42)
    assert all(parts[k] for k in dt.SPLITS)
    assert len(parts["train"]) > len(parts["val"]) >= len(parts["test"])


def test_split_rejects_bad_ratios():
    with pytest.raises(ValueError):
        dt.split_groups(_samples([("A", 1)]), (0.5, 0.2, 0.2), seed=1)


def _write(tmp: Path, name: str, label: str | None):
    (tmp / "images").mkdir(exist_ok=True)
    (tmp / "labels").mkdir(exist_ok=True)
    (tmp / "images" / f"{name}.jpg").write_bytes(b"jpg")
    if label is not None:
        (tmp / "labels" / f"{name}.txt").write_text(label, encoding="utf-8")


def test_prepare_dataset_end_to_end(tmp_path):
    for v in range(10):
        for i in range(2):
            _write(tmp_path, f"V{v}_{i}", "0 0.5 0.5 0.2 0.1\n")
    _write(tmp_path, "calle_vacia", None)
    out = tmp_path / "dataset"
    args = ["--images", str(tmp_path / "images"), "--labels", str(tmp_path / "labels"),
            "--output", str(out), "--group-regex", r"^(\w+?)_"]

    assert prepare_dataset.main(args) == 1          # foto sin etiquetas
    assert not out.exists()
    assert prepare_dataset.main(args + ["--allow-unlabeled"]) == 0
    yaml = (out / "data.yaml").read_text(encoding="utf-8")
    assert "0: license_plate" in yaml and f"path: {out.resolve()}" in yaml
    images = list((out / "images").rglob("*.jpg"))
    labels = list((out / "labels").rglob("*.txt"))
    assert len(images) == len(labels) == 21
    assert prepare_dataset.main(args + ["--allow-unlabeled"]) == 1  # ya existe, sin --force


def test_prepare_dataset_reports_bad_labels(tmp_path):
    _write(tmp_path, "a", "0 0.5 0.5 0.2 0.1\n")
    _write(tmp_path, "b", "3 0.5 0.5 0.2 0.1\n")
    out = tmp_path / "dataset"
    assert prepare_dataset.main(["--images", str(tmp_path / "images"), "--labels", str(tmp_path / "labels"),
                                 "--output", str(out)]) == 1
    assert not out.exists()
