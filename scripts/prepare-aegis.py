"""Piper metadata adaptation following sherpa-onnx scripts/piper/add_meta_data.py.
Upstream Copyright 2025 Xiaomi Corp., author Fangjun Kuang.
Aegis license and model card are bundled alongside the model.
"""
import json, sys
from pathlib import Path
import onnx
root = Path(sys.argv[1])
name = "ur_PK-aegis_female-medium.onnx"
config = json.loads((root / (name + ".json")).read_text(encoding="utf-8"))
assert config["espeak"]["voice"] == "ur"
with (root / "tokens.txt").open("w", encoding="utf-8") as f:
    for token, ids in config["phoneme_id_map"].items():
        if token != "\n":
            f.write(f"{token} {ids[0] if isinstance(ids, list) else ids}\n")
model = onnx.load(root / name)
del model.metadata_props[:]
for key, value in {"model_type":"vits", "comment":"piper", "language":"Urdu",
                   "voice":"ur", "version":1, "has_espeak":1, "has_g2pw":0,
                   "n_speakers":config["num_speakers"],
                   "sample_rate":config["audio"]["sample_rate"]}.items():
    prop = model.metadata_props.add()
    prop.key, prop.value = key, str(value)
onnx.save(model, root / name)
print("Aegis metadata and tokens prepared.")
