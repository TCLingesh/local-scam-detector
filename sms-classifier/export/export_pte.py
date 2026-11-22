import sys, json
sys.path.insert(0, "train")
import pandas as pd, torch
from torch.export import export
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
from executorch.runtime import Runtime
from common import SmsCharCNN, encode, load_vocab, MAX_LEN

vocab = load_vocab()
model = SmsCharCNN(len(vocab) + 2)
model.load_state_dict(torch.load("models/sms_charcnn.pt", weights_only=True))
model.eval()
thr = json.load(open("results/metrics.json"))["threshold"]

# 1. convert
example = (torch.zeros(1, MAX_LEN, dtype=torch.long),)
edge = to_edge_transform_and_lower(export(model, example), partitioner=[XnnpackPartitioner()])

# 2. delegation summary (wrapped so a failure here doesn't stop the export)
try:
    from executorch.devtools.backend_debug import get_delegation_info
    summary = get_delegation_info(edge.exported_program().graph_module).get_summary()
    print(summary)
    open("results/delegation_summary.txt", "w").write(summary)
except Exception as e:
    print("Could not print delegation summary:", e)

et = edge.to_executorch()
with open("models/sms_charcnn.pte", "wb") as f:
    f.write(et.buffer)
print("wrote models/sms_charcnn.pte,", len(et.buffer), "bytes")

# 3. check the .pte against PyTorch on the whole test set
method = Runtime.get().load_program("models/sms_charcnn.pte").load_method("forward")
df = pd.read_csv("data/test.csv")
max_diff, class_agree, decision_agree = 0.0, 0, 0
for t in df["text"]:
    ids = torch.tensor([encode(t, vocab)], dtype=torch.long)
    with torch.no_grad():
        ref = model(ids)
    out = method.execute([ids])[0]
    max_diff = max(max_diff, (ref - out).abs().max().item())
    class_agree += int(ref.argmax().item() == out.argmax().item())
    decision_agree += int((ref[0, 1] >= thr).item() == (out[0, 1] >= thr).item())
n = len(df)
res = {"n": n, "max_abs_diff": max_diff,
       "argmax_agreement": class_agree / n,
       "threshold_decision_agreement": decision_agree / n}
print(res)
json.dump(res, open("results/export_validation.json", "w"), indent=2)