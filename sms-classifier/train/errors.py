import json, numpy as np, pandas as pd, torch
from common import SmsCharCNN, encode, load_vocab

vocab = load_vocab()
model = SmsCharCNN(len(vocab) + 2)
model.load_state_dict(torch.load("models/sms_charcnn.pt", weights_only=True)); model.eval()
thr = json.load(open("results/metrics.json"))["threshold"]
df = pd.read_csv("data/test.csv")
X = torch.tensor([encode(t, vocab) for t in df["text"]], dtype=torch.long)
with torch.no_grad():
    p = model(X)[:, 1].numpy()
df["p"] = p; df["pred"] = (p >= thr).astype(int)
print("FALSE POSITIVES (ham flagged as spam):")
for _, r in df[(df.label == 0) & (df.pred == 1)].iterrows():
    print(f"  {r.p:.2f} | {r.text[:120]}")
print("\nFALSE NEGATIVES (spam missed):")
for _, r in df[(df.label == 1) & (df.pred == 0)].iterrows():
    print(f"  {r.p:.2f} | {r.text[:120]}")