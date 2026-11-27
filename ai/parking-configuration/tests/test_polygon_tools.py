import json

import pytest

import convert_polygons
import polygon_tools as pt
import validate_polygons


def square(x, y, size=100):
    return [(x, y), (x + size, y), (x + size, y + size), (x, y + size)]


def test_generate_and_parse_codes():
    assert pt.generate_codes(3, "a-", start=9, digits=2) == ["A-09", "A-10", "A-11"]
    assert pt.parse_codes("a-01, a-02\n# comentario\nb-01  # otro") == ["A-01", "A-02", "B-01"]


def test_order_by_rows_reads_left_to_right_top_to_bottom():
    # Dibujados desordenados y con la segunda fila un poco torcida
    drawn = [square(220, 205), square(0, 0), square(110, 210), square(110, 5), square(0, 200)]
    ordered = pt.order_by_rows(drawn)
    assert [pt.centroid(b) for b in ordered] == [pt.centroid(b) for b in
                                                 [square(0, 0), square(110, 5), square(0, 200),
                                                  square(110, 210), square(220, 205)]]


def test_assign_codes_requires_same_count_and_unique():
    with pytest.raises(ValueError, match="2 polígonos y 1"):
        pt.assign_codes([square(0, 0), square(200, 0)], ["A-01"])
    with pytest.raises(ValueError, match="repetidos"):
        pt.assign_codes([square(0, 0), square(200, 0)], ["A-01", "A-01"])


def test_geometry():
    assert pt.area(square(0, 0)) == 10000
    assert pt.centroid(square(0, 0)) == (50, 50)
    assert pt.is_simple(square(0, 0))
    # Esquinas en orden cruzado: forma de "ocho"
    assert not pt.is_simple([(0, 0), (100, 100), (100, 0), (0, 100)])
    assert pt.overlap_ratio(square(0, 0), square(300, 0)) == 0
    assert pt.overlap_ratio(square(0, 0), square(50, 0)) == pytest.approx(0.5, abs=0.02)


def test_parse_rejects_ultralytics_and_bad_shapes():
    _, issues = pt.parse_polygons([{"points": [[0, 0], [1, 0], [1, 1]]}])
    assert "Ultralytics" in issues[0].message

    polygons, issues = pt.parse_polygons({
        "A-01": [[0, 0], [100, 0], [100, 100]],
        "a-02": [[0, 0], [100, 0], [100, 100]],
        "A-03": [[0, 0], [100, 0]],
        "A-04": [[0, 0], [100, "x"], [100, 100]],
        "A-05": [[0, 0], [100, True], [100, 100]],
    })
    assert list(polygons) == ["A-01"]
    assert {i.space for i in issues} == {"a-02", "A-03", "A-04", "A-05"}


def test_validate_reports_problems():
    polygons = {
        "A-01": square(0, 0),
        "A-02": square(50, 0),                 # 50 % sobre A-01
        "A-03": square(980, 0),                # se sale de 1000 px de ancho
        "A-04": square(400, 400, size=5),      # muy pequeño
        "A-05": [(600, 600), (700, 700), (700, 600), (600, 700)],  # lados cruzados
    }
    issues = pt.validate(polygons, image_size=(1000, 800), expected_codes=["A-01", "A-02", "A-03", "A-04", "A-06"])
    text = "\n".join(map(str, issues))
    assert "Se superpone 50% con A-02" in text
    assert "Se sale de la imagen" in text
    assert "demasiado pequeña" in text
    assert "se cruzan" in text
    assert "Faltan espacios: A-06" in text
    assert "no existen en la zona: A-05" in text


def test_small_overlap_is_only_a_warning():
    issues = pt.validate({"A-01": square(0, 0), "A-02": square(97, 0)})
    assert [i.level for i in issues] == ["WARNING"]


def test_duplicate_keys_are_rejected(tmp_path):
    f = tmp_path / "p.json"
    f.write_text('{"A-01": [[0,0],[1,0],[1,1]], "A-01": [[0,0],[2,0],[2,2]]}', encoding="utf-8")
    with pytest.raises(ValueError, match="dos veces"):
        pt.load_json(f)


def test_convert_then_validate_end_to_end(tmp_path):
    from PIL import Image

    image = tmp_path / "foto.jpg"
    Image.new("RGB", (640, 480), "gray").save(image)
    boxes = tmp_path / "bounding_boxes.json"
    # Como lo guarda Ultralytics: lista de {"points"} con tuplas convertidas a listas
    boxes.write_text(json.dumps([{"points": square(150, 0)}, {"points": square(0, 0)},
                                 {"points": square(0, 200)}]), encoding="utf-8")
    out = tmp_path / "parking-1-zone-A.json"

    assert convert_polygons.main(["--input", str(boxes), "--prefix", "A-", "--image", str(image),
                                  "--output", str(out)]) == 0
    saved = json.loads(out.read_text(encoding="utf-8"))
    assert list(saved) == ["A-01", "A-02", "A-03"]
    assert saved["A-01"] == [[0, 0], [100, 0], [100, 100], [0, 100]]
    assert (tmp_path / "parking-1-zone-A-preview.jpg").exists()

    # No sobrescribe sin --force
    assert convert_polygons.main(["--input", str(boxes), "--prefix", "A-", "--output", str(out)]) == 1

    codes = tmp_path / "codigos.txt"
    codes.write_text("A-01\nA-02\nA-03\n", encoding="utf-8")
    assert validate_polygons.main(["--json", str(out), "--image", str(image), "--codes-file", str(codes)]) == 0
    codes.write_text("A-01\nA-02\n", encoding="utf-8")
    assert validate_polygons.main(["--json", str(out), "--codes-file", str(codes)]) == 1


def test_convert_refuses_overlapping_drawing(tmp_path):
    boxes = tmp_path / "bounding_boxes.json"
    boxes.write_text(json.dumps([{"points": square(0, 0)}, {"points": square(30, 0)}]), encoding="utf-8")
    out = tmp_path / "out.json"
    assert convert_polygons.main(["--input", str(boxes), "--codes", "A-01,A-02", "--output", str(out)]) == 1
    assert not out.exists()
