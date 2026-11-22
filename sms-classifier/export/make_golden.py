import sys, json
sys.path.insert(0, "train")
import pandas as pd, torch
from common import SmsCharCNN, encode, load_vocab

vocab = load_vocab()
model = SmsCharCNN(len(vocab) + 2)
model.load_state_dict(torch.load("models/sms_charcnn.pt", weights_only=True)); model.eval()
thr = json.load(open("results/metrics.json"))["threshold"]

df = pd.read_csv("data/test.csv")
texts = list(df[df.label == 1].sample(15, random_state=1)["text"]) + \
        list(df[df.label == 0].sample(15, random_state=1)["text"])

edge_cases = [
  "",
  "   ",
  "WIN a FREE prize now!!! Click http://bit.ly/x to claim",
  "Your OTP is 482913. Do not share it with anyone.",
  "Dear customer your KYC has expired. Update now at http://kyc-update.xyz or your account will be blocked",
  "HDFC Bank: Rs 2,500 debited from a/c XX1234 on 06-11-26. Not you? Call 1800-xxx",
  "Congratulations! You won Rs 10,00,000 in UPI lucky draw. Send your bank details to claim",
  "Hey, are we still meeting at 6 today? 😀",
  "Free entry 🎉🎉 text WIN to 80082 now 💰",
  "Café menu: crème brûlée, naïve résumé — ÀÉÎÕÜ",
  "İSTANBUL STRASSE ß ǅ",
  "நன்றி, நாளை சந்திப்போம்",
  "a" * 400,
  "URGENT! " * 40,
]
cases = []
for t in texts + edge_cases:
    ids = encode(t, vocab)
    with torch.no_grad():
        p = model(torch.tensor([ids], dtype=torch.long))[0].tolist()
    cases.append({"text": t, "token_ids": ids, "expected_probs": p,
                  "expected_is_scam": p[1] >= thr})
json.dump({"threshold": thr, "max_len": 256, "tolerance": 1e-4, "cases": cases},
          open("models/golden_test.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("wrote models/golden_test.json with", len(cases), "cases")