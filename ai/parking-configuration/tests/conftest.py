import sys
from pathlib import Path

# Los scripts se ejecutan desde scripts/: se importan igual en las pruebas
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))
