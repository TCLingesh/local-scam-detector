import sys, json
sys.path.insert(0, "train")
from common import MAX_LEN, PAD, UNK

thr = json.load(open("results/metrics.json"))["threshold"]
spec = {
  "name": "sms_charcnn",
  "model_file": "sms_charcnn.pte",
  "vocab_file": "vocab.json",
  "input": {
    "shape": [1, MAX_LEN],
    "dtype": "int64",
    "preprocessing": [
      "1. lowercase the whole text (Python str.lower(); Java: toLowerCase(Locale.ROOT))",
      "2. keep only the first max_len CODE POINTS (not UTF-16 chars)",
      "3. map each code point to its index in vocab.json; unknown -> unk_index",
      "4. pad with pad_index up to max_len"
    ],
    "max_len": MAX_LEN, "pad_index": PAD, "unk_index": UNK
  },
  "output": {
    "shape": [1, 2], "dtype": "float32",
    "labels": ["legit", "scam"],
    "postprocessing": "softmax is already inside the model; scam if output[1] >= threshold"
  },
  "threshold": thr
}
json.dump(spec, open("models/model_spec.json", "w"), indent=2)
print("wrote models/model_spec.json, threshold =", thr)